package com.facemorphapp.engine

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.facemorphapp.domain.model.FaceDetectionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MIN_CONFIDENCE = 0.85f

/**
 * Wraps the ML Kit Face Detection API.  Models are bundled in the APK via
 * manifestPlaceholders["mlkitFaceDetectionModuleType"] = "local_model"
 * — no download occurs at runtime.
 */
@Singleton
class FaceDetectorWrapper @Inject constructor() {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    suspend fun detectFaces(bitmap: Bitmap): Result<List<FaceDetectionResult>> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val results = faces
                        .filter { it.trackingId != null || faces.size == 1 }
                        .mapNotNull { it.toDetectionResult(bitmap.width, bitmap.height) }
                        .filter { it.confidence >= MIN_CONFIDENCE }
                    cont.resume(Result.success(results))
                }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
            cont.invokeOnCancellation { detector.close() }
        }

    private fun Face.toDetectionResult(imgW: Int, imgH: Int): FaceDetectionResult? {
        val box = RectF(
            boundingBox.left.toFloat(), boundingBox.top.toFloat(),
            boundingBox.right.toFloat(), boundingBox.bottom.toFloat()
        )

        // ML Kit provides a small set of named landmarks; we build a dense set from them.
        val leftEye   = getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { PointF(it.x, it.y) }
        val rightEye  = getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { PointF(it.x, it.y) }
        val noseBase  = getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { PointF(it.x, it.y) }
        val mouthLeft = getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { PointF(it.x, it.y) }
        val mouthRight= getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { PointF(it.x, it.y) }

        if (leftEye == null || rightEye == null || noseBase == null) return null

        val mouthCentre = when {
            mouthLeft != null && mouthRight != null ->
                PointF((mouthLeft.x + mouthRight.x) / 2f, (mouthLeft.y + mouthRight.y) / 2f)
            mouthLeft != null -> mouthLeft
            mouthRight != null -> mouthRight
            else -> PointF(noseBase.x, noseBase.y + (noseBase.y - leftEye.y) * 0.5f)
        }

        // Build a synthesised 17-point face contour for Delaunay from bounding box
        val landmarks = buildSyntheticLandmarks(box, leftEye, rightEye, noseBase, mouthCentre)

        // Use headEulerAngleY-based confidence approximation when ML Kit doesn't
        // expose a raw detection confidence.  Frontal faces (|yaw| < 30°) get
        // bonus; extreme side views are penalised.
        val yawPenalty = 1f - (kotlin.math.abs(headEulerAngleY) / 90f).coerceIn(0f, 0.5f)
        val conf = (smilingProbability ?: 0.5f).let { yawPenalty * 0.9f + 0.1f }

        return FaceDetectionResult(
            boundingBox     = box,
            landmarks       = landmarks,
            confidence      = conf.coerceIn(0f, 1f),
            leftEyeCenter   = leftEye,
            rightEyeCenter  = rightEye,
            noseTip         = noseBase,
            mouthCenter     = mouthCentre
        )
    }

    /**
     * Creates a 17-point polygon (jawline approximation + 4 anchor points)
     * from named ML Kit landmarks.  In a production app you would use a full
     * 468-point mesh from MediaPipe Face Mesh; here we interpolate from ML Kit's
     * sparse set to keep the dependency list minimal.
     */
    private fun buildSyntheticLandmarks(
        box: RectF,
        leftEye: PointF, rightEye: PointF,
        nose: PointF, mouth: PointF
    ): List<PointF> {
        val w = box.width(); val h = box.height()
        val cx = box.centerX(); val cy = box.centerY()
        // 17-point jawline + key features
        return listOf(
            // Jawline (0–16)
            PointF(box.left,       box.bottom - h * 0.1f),
            PointF(box.left + w * 0.05f, box.bottom),
            PointF(box.left + w * 0.15f, box.bottom + h * 0.05f),
            PointF(box.left + w * 0.25f, box.bottom + h * 0.08f),
            PointF(box.left + w * 0.35f, box.bottom + h * 0.10f),
            PointF(box.left + w * 0.45f, box.bottom + h * 0.11f),
            PointF(cx,             box.bottom + h * 0.11f),
            PointF(box.left + w * 0.55f, box.bottom + h * 0.11f),
            PointF(box.left + w * 0.65f, box.bottom + h * 0.10f),
            PointF(box.left + w * 0.75f, box.bottom + h * 0.08f),
            PointF(box.left + w * 0.85f, box.bottom + h * 0.05f),
            PointF(box.right - w * 0.05f, box.bottom),
            PointF(box.right,      box.bottom - h * 0.1f),
            // Eyes, nose, mouth (13–16)
            leftEye, rightEye, nose, mouth
        )
    }
}
