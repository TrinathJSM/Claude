package com.facemorphapp.engine

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.FaceValidationResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MIN_CONFIDENCE = 0.70f
private const val MIN_FACE_FRACTION = 0.03f  // face must cover ≥3% of image area

/**
 * Wraps the ML Kit Face Detection API with CONTOUR_MODE_ALL enabled.
 *
 * CONTOUR_MODE_ALL provides dense outlines for the face, eyes, eyebrows, nose,
 * and lips — typically 130–150 points per face — giving a much richer mesh than
 * the 8-point sparse landmark mode for the Delaunay warp step.
 *
 * Note: CONTOUR_MODE_ALL only returns the single most-prominent face and requires
 * PERFORMANCE_MODE_FAST (the SDK silently falls back if ACCURATE is requested).
 * Models are bundled in the APK — no download occurs at runtime.
 */
@Singleton
class FaceDetectorWrapper @Inject constructor() {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    suspend fun detectFaces(bitmap: Bitmap): Result<List<FaceDetectionResult>> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val results = faces.mapNotNull { it.toDetectionResult(bitmap.width, bitmap.height) }
                    cont.resume(Result.success(results))
                }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
            cont.invokeOnCancellation { detector.close() }
        }

    suspend fun validateFace(bitmap: Bitmap): FaceValidationResult {
        val minFaceArea = bitmap.width * bitmap.height * MIN_FACE_FRACTION
        return detectFaces(bitmap).fold(
            onSuccess = { faces ->
                when {
                    faces.isEmpty() -> FaceValidationResult.NoFace
                    faces.size > 1 -> FaceValidationResult.MultipleFaces(faces)
                    faces.first().boundingBox.let { it.width() * it.height() } < minFaceArea ->
                        FaceValidationResult.TooSmall
                    faces.first().confidence < MIN_CONFIDENCE ->
                        FaceValidationResult.LowConfidence
                    else -> FaceValidationResult.Valid(faces.first())
                }
            },
            onFailure = { FaceValidationResult.NoFace }
        )
    }

    private fun Face.toDetectionResult(imgW: Int, imgH: Int): FaceDetectionResult? {
        val box = RectF(
            boundingBox.left.toFloat(), boundingBox.top.toFloat(),
            boundingBox.right.toFloat(), boundingBox.bottom.toFloat()
        )

        val leftEye   = getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { PointF(it.x, it.y) }
        val rightEye  = getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { PointF(it.x, it.y) }
        val noseBase  = getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { PointF(it.x, it.y) }
        val mouthLeft = getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { PointF(it.x, it.y) }
        val mouthRight= getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { PointF(it.x, it.y) }

        // Anchor point fallbacks when specific landmarks aren't detected
        val leftEyeCenter  = leftEye   ?: contourCenter(FaceContour.LEFT_EYE)  ?: PointF(box.left  + box.width() * 0.3f, box.centerY() - box.height() * 0.1f)
        val rightEyeCenter = rightEye  ?: contourCenter(FaceContour.RIGHT_EYE) ?: PointF(box.right - box.width() * 0.3f, box.centerY() - box.height() * 0.1f)
        val noseTip        = noseBase  ?: contourTip(FaceContour.NOSE_BOTTOM)  ?: PointF(box.centerX(), box.centerY())
        val mouthCenter = when {
            mouthLeft != null && mouthRight != null ->
                PointF((mouthLeft.x + mouthRight.x) / 2f, (mouthLeft.y + mouthRight.y) / 2f)
            mouthLeft  != null -> mouthLeft
            mouthRight != null -> mouthRight
            else -> contourCenter(FaceContour.LOWER_LIP_BOTTOM)
                ?: PointF(box.centerX(), box.centerY() + box.height() * 0.2f)
        }

        // Extract dense mesh from all available contour types
        val allContourPoints = extractAllContourPoints()
        val faceOutline = getContour(FaceContour.FACE)?.points
            ?.map { PointF(it.x, it.y) }
            ?: buildBoxOutline(box)

        val landmarks = allContourPoints.ifEmpty {
            buildSyntheticLandmarks(box, leftEyeCenter, rightEyeCenter, noseTip, mouthCenter)
        }

        val yawPenalty = 1f - (kotlin.math.abs(headEulerAngleY) / 90f).coerceIn(0f, 0.4f)
        val conf = yawPenalty * 0.85f + 0.15f

        return FaceDetectionResult(
            boundingBox    = box,
            landmarks      = landmarks,
            faceOutline    = faceOutline,
            confidence     = conf.coerceIn(0f, 1f),
            leftEyeCenter  = leftEyeCenter,
            rightEyeCenter = rightEyeCenter,
            noseTip        = noseTip,
            mouthCenter    = mouthCenter
        )
    }

    // ── Contour extraction ─────────────────────────────────────────────────────

    private val CONTOUR_TYPES = listOf(
        FaceContour.FACE,
        FaceContour.LEFT_EYE,             FaceContour.RIGHT_EYE,
        FaceContour.LEFT_EYEBROW_TOP,     FaceContour.LEFT_EYEBROW_BOTTOM,
        FaceContour.RIGHT_EYEBROW_TOP,    FaceContour.RIGHT_EYEBROW_BOTTOM,
        FaceContour.UPPER_LIP_TOP,        FaceContour.UPPER_LIP_BOTTOM,
        FaceContour.LOWER_LIP_TOP,        FaceContour.LOWER_LIP_BOTTOM,
        FaceContour.NOSE_BRIDGE,          FaceContour.NOSE_BOTTOM
    )

    private fun Face.extractAllContourPoints(): List<PointF> {
        val pts = mutableListOf<PointF>()
        for (type in CONTOUR_TYPES) {
            getContour(type)?.points?.forEach { p -> pts.add(PointF(p.x, p.y)) }
        }
        return pts
    }

    private fun Face.contourCenter(type: Int): PointF? {
        val pts = getContour(type)?.points ?: return null
        if (pts.isEmpty()) return null
        return PointF(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size,
                      pts.sumOf { it.y.toDouble() }.toFloat() / pts.size)
    }

    private fun Face.contourTip(type: Int): PointF? {
        val pts = getContour(type)?.points ?: return null
        val mid = pts.size / 2
        return pts.getOrNull(mid)?.let { PointF(it.x, it.y) }
    }

    // ── Fallback when contours unavailable ────────────────────────────────────

    private fun buildSyntheticLandmarks(
        box: RectF, leftEye: PointF, rightEye: PointF,
        nose: PointF, mouth: PointF
    ): List<PointF> {
        val w = box.width(); val h = box.height()
        val cx = box.centerX()
        return listOf(
            PointF(box.left,                    box.bottom - h * 0.1f),
            PointF(box.left  + w * 0.05f,       box.bottom),
            PointF(box.left  + w * 0.15f,       box.bottom + h * 0.05f),
            PointF(box.left  + w * 0.25f,       box.bottom + h * 0.08f),
            PointF(box.left  + w * 0.35f,       box.bottom + h * 0.10f),
            PointF(box.left  + w * 0.45f,       box.bottom + h * 0.11f),
            PointF(cx,                           box.bottom + h * 0.11f),
            PointF(box.left  + w * 0.55f,       box.bottom + h * 0.11f),
            PointF(box.left  + w * 0.65f,       box.bottom + h * 0.10f),
            PointF(box.left  + w * 0.75f,       box.bottom + h * 0.08f),
            PointF(box.left  + w * 0.85f,       box.bottom + h * 0.05f),
            PointF(box.right - w * 0.05f,       box.bottom),
            PointF(box.right,                   box.bottom - h * 0.1f),
            leftEye, rightEye, nose, mouth
        )
    }

    private fun buildBoxOutline(box: RectF): List<PointF> {
        val cx = box.centerX(); val cy = box.centerY()
        val rx = box.width() / 2f; val ry = box.height() / 2f
        return (0 until 36).map { i ->
            val a = Math.PI * 2.0 * i / 36.0
            PointF(cx + rx * kotlin.math.cos(a).toFloat(), cy + ry * kotlin.math.sin(a).toFloat())
        }
    }
}
