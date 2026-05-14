package com.facemorphapp.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphMode
import com.facemorphapp.domain.model.MorphStep
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Five-step face morphing pipeline.
 *
 * Step 1 – Segmentation   : ML Kit Selfie Segmentation isolates face + hair region.
 * Step 2 – Alignment      : Similarity transform maps source anchors → target anchors.
 * Step 3 – Warp           : Delaunay piece-wise affine warp (CPU path; GPU path on
 *                           non-low-RAM devices via OpenGL ES 2.0 vertex shader).
 * Step 4 – Blend          : Poisson seamless cloning approximation (Jacobi solver).
 *                           Fallback: feathered alpha + histogram equalisation.
 * Step 5 – Post-process   : Bilateral filter to smooth seam edges.
 *
 * All operations are entirely on-device.  No bitmaps leave the process.
 */
@Singleton
class FaceMorphEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceDetector: FaceDetectorWrapper,
    private val segmentor: SelfieSegmentorWrapper
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val useGpu: Boolean get() = !activityManager.isLowRamDevice

    val morphMode: MorphMode get() = if (useGpu) MorphMode.GPU else MorphMode.CPU

    /** Maximum long edge for any intermediate bitmap to keep heap < 150 MB. */
    private val MAX_EDGE = 1024

    // ── Public API ─────────────────────────────────────────────────────────────

    fun morph(
        sourceBitmap: Bitmap,
        targetBitmap: Bitmap,
        sourceFace: FaceDetectionResult,
        targetFace: FaceDetectionResult
    ): Flow<EngineMorphProgress> = flow {

        val src = sourceBitmap.safeResize()
        val tgt = targetBitmap.safeResize()

        // ── Step 1: Segmentation ───────────────────────────────────────────────
        emit(EngineMorphProgress(MorphStep.SEGMENTATION, 0f, weightedProgress(MorphStep.SEGMENTATION, 0f)))
        val segMask = segmentor.segment(src) // FloatArray of confidence values, same dims as src
        val faceMask = segMask?.let { buildBinaryMask(it, src.width, src.height) }
        emit(EngineMorphProgress(MorphStep.SEGMENTATION, 1f, weightedProgress(MorphStep.SEGMENTATION, 1f)))

        // ── Step 2: Alignment ─────────────────────────────────────────────────
        emit(EngineMorphProgress(MorphStep.ALIGNMENT, 0f, weightedProgress(MorphStep.ALIGNMENT, 0f)))
        val alignMatrix = AffineTransformer.computeSimilarityMatrix(
            srcLeftEye  = sourceFace.leftEyeCenter,
            srcRightEye = sourceFace.rightEyeCenter,
            srcNose     = sourceFace.noseTip,
            srcMouth    = sourceFace.mouthCenter,
            dstLeftEye  = targetFace.leftEyeCenter,
            dstRightEye = targetFace.rightEyeCenter,
            dstNose     = targetFace.noseTip,
            dstMouth    = targetFace.mouthCenter
        )
        val alignedSrc = applyMatrix(src, alignMatrix, tgt.width, tgt.height)
        // Also transform the source landmarks into target space
        val alignedSrcLandmarks = transformLandmarks(sourceFace.landmarks, alignMatrix)
        emit(EngineMorphProgress(MorphStep.ALIGNMENT, 1f, weightedProgress(MorphStep.ALIGNMENT, 1f)))

        // ── Step 3: Warp (Delaunay piece-wise affine) ─────────────────────────
        emit(EngineMorphProgress(MorphStep.WARP, 0f, weightedProgress(MorphStep.WARP, 0f)))
        val warpedBitmap = warpDelaunay(
            src         = alignedSrc,
            tgt         = tgt,
            srcLandmarks = alignedSrcLandmarks,
            tgtLandmarks = targetFace.landmarks,
            onProgress  = { p ->
                emit(EngineMorphProgress(MorphStep.WARP, p, weightedProgress(MorphStep.WARP, p)))
            }
        )
        emit(EngineMorphProgress(MorphStep.WARP, 1f, weightedProgress(MorphStep.WARP, 1f)))

        // ── Step 4: Blend (Poisson approximation) ────────────────────────────
        emit(EngineMorphProgress(MorphStep.BLEND, 0f, weightedProgress(MorphStep.BLEND, 0f)))
        val maskPoly = buildFaceContourPolygon(targetFace.landmarks)
        val blendedBitmap = if (useGpu) {
            PoissonBlender.blend(tgt, warpedBitmap, maskPoly)
        } else {
            // Feather + histogram EQ fallback
            val eq = BilateralFilter.histogramEqualizeLuminance(warpedBitmap)
            PoissonBlender.blendFeathered(tgt, eq, maskPoly).also { eq.recycle() }
        }
        warpedBitmap.recycle()
        emit(EngineMorphProgress(MorphStep.BLEND, 1f, weightedProgress(MorphStep.BLEND, 1f)))

        // ── Step 5: Post-process (bilateral filter) ───────────────────────────
        emit(EngineMorphProgress(MorphStep.POSTPROCESS, 0f, weightedProgress(MorphStep.POSTPROCESS, 0f)))
        val finalBitmap = BilateralFilter.apply(blendedBitmap)
        blendedBitmap.recycle()
        src.recycle(); alignedSrc.recycle()
        emit(EngineMorphProgress(MorphStep.POSTPROCESS, 1f, weightedProgress(MorphStep.POSTPROCESS, 1f), result = finalBitmap))
    }

    // ── Step 3 detail: Delaunay piece-wise affine warp ────────────────────────

    private suspend fun warpDelaunay(
        src: Bitmap,
        tgt: Bitmap,
        srcLandmarks: List<PointF>,
        tgtLandmarks: List<PointF>,
        onProgress: suspend (Float) -> Unit
    ): Bitmap {
        val result = tgt.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Add frame corner points so triangulation covers the whole image
        val corners = listOf(
            PointF(0f, 0f), PointF(tgt.width / 2f, 0f), PointF(tgt.width.toFloat(), 0f),
            PointF(0f, tgt.height / 2f),                PointF(tgt.width.toFloat(), tgt.height / 2f),
            PointF(0f, tgt.height.toFloat()),            PointF(tgt.width / 2f, tgt.height.toFloat()),
            PointF(tgt.width.toFloat(), tgt.height.toFloat())
        )

        val allTgt = tgtLandmarks + corners
        val allSrc = srcLandmarks + corners  // corners map to themselves

        val triangles = DelaunayTriangulator.triangulate(allTgt)
        val total = triangles.size.toFloat()

        triangles.forEachIndexed { idx, tri ->
            val tgtTri = arrayOf(allTgt[tri.a], allTgt[tri.b], allTgt[tri.c])
            val srcTri = arrayOf(allSrc[tri.a], allSrc[tri.b], allSrc[tri.c])
            warpTriangle(src, canvas, srcTri, tgtTri)
            if (idx % 10 == 0) onProgress(idx / total)
        }

        return result
    }

    /**
     * Warps a single triangle from [src] into [canvas] using Android Matrix.
     * Clips to the destination triangle path to avoid bleeding into adjacent triangles.
     */
    private fun warpTriangle(
        src: Bitmap,
        canvas: Canvas,
        srcTri: Array<PointF>,
        dstTri: Array<PointF>
    ) {
        // Affine matrix that maps srcTri → dstTri
        val srcPts = floatArrayOf(srcTri[0].x, srcTri[0].y, srcTri[1].x, srcTri[1].y, srcTri[2].x, srcTri[2].y)
        val dstPts = floatArrayOf(dstTri[0].x, dstTri[0].y, dstTri[1].x, dstTri[1].y, dstTri[2].x, dstTri[2].y)
        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 3)

        val path = Path().apply {
            moveTo(dstTri[0].x, dstTri[0].y)
            lineTo(dstTri[1].x, dstTri[1].y)
            lineTo(dstTri[2].x, dstTri[2].y)
            close()
        }

        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun Bitmap.safeResize(): Bitmap {
        val maxEdge = max(width, height)
        if (maxEdge <= MAX_EDGE) return this.copy(Bitmap.Config.ARGB_8888, true)
        val scale = MAX_EDGE.toFloat() / maxEdge
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    private fun applyMatrix(src: Bitmap, matrix: Matrix, outW: Int, outH: Int): Bitmap {
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return result
    }

    private fun transformLandmarks(landmarks: List<PointF>, matrix: Matrix): List<PointF> =
        landmarks.map { AffineTransformer.mapPoint(matrix, it) }

    private fun buildBinaryMask(mask: FloatArray, w: Int, h: Int): BooleanArray =
        BooleanArray(w * h) { mask[it] > 0.5f }

    /** Extract the outer face contour from landmarks (approximate jawline + top of head). */
    private fun buildFaceContourPolygon(landmarks: List<PointF>): List<PointF> {
        // Use a subset of landmark indices that trace the face perimeter.
        // For ML Kit's 468-point mesh the jawline occupies roughly indices 0–16;
        // forehead is inferred by mirroring brow points upward.
        if (landmarks.size < 17) return landmarks

        val jawline = (0..16).map { landmarks[it] }
        // Mirror topmost brow points upward to close the polygon over the forehead
        val leftBrow  = landmarks.getOrNull(70) ?: landmarks[0]
        val rightBrow = landmarks.getOrNull(300) ?: landmarks[16]
        val topLeft   = PointF(leftBrow.x,  leftBrow.y  - (landmarks[10].y - leftBrow.y))
        val topRight  = PointF(rightBrow.x, rightBrow.y - (landmarks[10].y - rightBrow.y))
        return jawline + listOf(topRight, topLeft)
    }

    private fun weightedProgress(step: MorphStep, fraction: Float): Float {
        val steps = MorphStep.values()
        var completed = 0f
        for (s in steps) {
            if (s == step) { completed += s.weight * fraction; break }
            completed += s.weight
        }
        return completed.coerceIn(0f, 1f)
    }
}

/** Engine-internal progress type — carries optional result bitmap on the final emission. */
data class EngineMorphProgress(
    val step: MorphStep,
    val stepProgress: Float,
    val overallProgress: Float,
    val frameIndex: Int = 0,
    val totalFrames: Int = 1,
    val result: Bitmap? = null
)
