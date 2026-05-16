package com.facemorphapp.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
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

/**
 * Six-step face morphing pipeline:
 *
 * 1. Segmentation   — ML Kit Selfie Segmentation isolates face + hair region.
 * 2. Alignment      — Similarity transform aligns source anchor landmarks → target.
 * 3. Warp           — Bowyer-Watson Delaunay + piece-wise affine warp per triangle.
 * 4. Blend          — Poisson seamless cloning (Jacobi solver, 50 iterations).
 * 5. Color Correction — Per-channel mean/stddev skin-tone matching (Reinhard 2001).
 * 6. Post-process   — Bilateral filter smooths seam edges.
 *
 * All operations are entirely on-device. No bitmaps leave the process.
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

    private val MAX_EDGE = 1024

    fun morph(
        sourceBitmap: Bitmap,
        targetBitmap: Bitmap,
        sourceFace: FaceDetectionResult,
        targetFace: FaceDetectionResult
    ): Flow<EngineMorphProgress> = flow {

        val src = sourceBitmap.safeResize()
        val tgt = targetBitmap.safeResize()

        // Scale face landmarks to match resized bitmap dimensions
        val srcScaleX = src.width.toFloat() / sourceBitmap.width
        val srcScaleY = src.height.toFloat() / sourceBitmap.height
        val tgtScaleX = tgt.width.toFloat() / targetBitmap.width
        val tgtScaleY = tgt.height.toFloat() / targetBitmap.height

        val scaledSourceFace = sourceFace.scaledTo(srcScaleX, srcScaleY)
        val scaledTargetFace = targetFace.scaledTo(tgtScaleX, tgtScaleY)

        // ── Step 1: Segmentation ───────────────────────────────────────────────
        emit(EngineMorphProgress(MorphStep.SEGMENTATION, 0f, weighted(MorphStep.SEGMENTATION, 0f)))
        val segMask = segmentor.segment(src)
        emit(EngineMorphProgress(MorphStep.SEGMENTATION, 1f, weighted(MorphStep.SEGMENTATION, 1f)))

        // ── Step 2: Alignment ─────────────────────────────────────────────────
        emit(EngineMorphProgress(MorphStep.ALIGNMENT, 0f, weighted(MorphStep.ALIGNMENT, 0f)))
        val alignMatrix = AffineTransformer.computeSimilarityMatrix(
            srcLeftEye  = scaledSourceFace.leftEyeCenter,
            srcRightEye = scaledSourceFace.rightEyeCenter,
            srcNose     = scaledSourceFace.noseTip,
            srcMouth    = scaledSourceFace.mouthCenter,
            dstLeftEye  = scaledTargetFace.leftEyeCenter,
            dstRightEye = scaledTargetFace.rightEyeCenter,
            dstNose     = scaledTargetFace.noseTip,
            dstMouth    = scaledTargetFace.mouthCenter
        )
        val alignedSrc = applyMatrix(src, alignMatrix, tgt.width, tgt.height)
        val alignedSrcLandmarks = transformLandmarks(scaledSourceFace.landmarks, alignMatrix)
        emit(EngineMorphProgress(MorphStep.ALIGNMENT, 1f, weighted(MorphStep.ALIGNMENT, 1f)))

        // ── Step 3: Warp (Delaunay piece-wise affine) ─────────────────────────
        emit(EngineMorphProgress(MorphStep.WARP, 0f, weighted(MorphStep.WARP, 0f)))
        val warpedBitmap = warpDelaunay(
            src          = alignedSrc,
            tgt          = tgt,
            srcLandmarks = alignedSrcLandmarks,
            tgtLandmarks = scaledTargetFace.landmarks,
            onProgress   = { p -> emit(EngineMorphProgress(MorphStep.WARP, p, weighted(MorphStep.WARP, p))) }
        )
        emit(EngineMorphProgress(MorphStep.WARP, 1f, weighted(MorphStep.WARP, 1f)))

        // ── Step 4: Blend (Poisson seamless cloning) ─────────────────────────
        emit(EngineMorphProgress(MorphStep.BLEND, 0f, weighted(MorphStep.BLEND, 0f)))
        val maskPoly = scaledTargetFace.faceOutline.ifEmpty { scaledTargetFace.landmarks }
        val blendedBitmap = if (useGpu) {
            PoissonBlender.blend(tgt, warpedBitmap, maskPoly)
        } else {
            val eq = BilateralFilter.histogramEqualizeLuminance(warpedBitmap)
            PoissonBlender.blendFeathered(tgt, eq, maskPoly).also { eq.recycle() }
        }
        warpedBitmap.recycle()
        emit(EngineMorphProgress(MorphStep.BLEND, 1f, weighted(MorphStep.BLEND, 1f)))

        // ── Step 5: Color correction ──────────────────────────────────────────
        emit(EngineMorphProgress(MorphStep.COLOR_CORRECTION, 0f, weighted(MorphStep.COLOR_CORRECTION, 0f)))
        val colorCorrected = ColorCorrector.correct(blendedBitmap, tgt, maskPoly)
        blendedBitmap.recycle()
        emit(EngineMorphProgress(MorphStep.COLOR_CORRECTION, 1f, weighted(MorphStep.COLOR_CORRECTION, 1f)))

        // ── Step 6: Post-process (bilateral filter) ───────────────────────────
        emit(EngineMorphProgress(MorphStep.POSTPROCESS, 0f, weighted(MorphStep.POSTPROCESS, 0f)))
        val finalBitmap = BilateralFilter.apply(colorCorrected)
        colorCorrected.recycle()
        src.recycle()
        alignedSrc.recycle()
        emit(EngineMorphProgress(MorphStep.POSTPROCESS, 1f, weighted(MorphStep.POSTPROCESS, 1f), result = finalBitmap))
    }

    // ── Delaunay piece-wise affine warp ────────────────────────────────────────

    private suspend fun warpDelaunay(
        src: Bitmap,
        tgt: Bitmap,
        srcLandmarks: List<PointF>,
        tgtLandmarks: List<PointF>,
        onProgress: suspend (Float) -> Unit
    ): Bitmap {
        val result = tgt.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val corners = listOf(
            PointF(0f, 0f),              PointF(tgt.width / 2f, 0f),        PointF(tgt.width.toFloat(), 0f),
            PointF(0f, tgt.height / 2f),                                     PointF(tgt.width.toFloat(), tgt.height / 2f),
            PointF(0f, tgt.height.toFloat()), PointF(tgt.width / 2f, tgt.height.toFloat()), PointF(tgt.width.toFloat(), tgt.height.toFloat())
        )

        val allTgt = tgtLandmarks + corners
        val allSrc = srcLandmarks  + corners

        val triangles = DelaunayTriangulator.triangulate(allTgt)
        val total = triangles.size.toFloat()

        triangles.forEachIndexed { idx, tri ->
            val tgtTri = arrayOf(allTgt[tri.a], allTgt[tri.b], allTgt[tri.c])
            val srcTri = arrayOf(allSrc.getOrElse(tri.a) { allTgt[tri.a] },
                                 allSrc.getOrElse(tri.b) { allTgt[tri.b] },
                                 allSrc.getOrElse(tri.c) { allTgt[tri.c] })
            warpTriangle(src, canvas, srcTri, tgtTri)
            if (idx % 10 == 0) onProgress(idx / total)
        }

        return result
    }

    private fun warpTriangle(
        src: Bitmap, canvas: Canvas,
        srcTri: Array<PointF>, dstTri: Array<PointF>
    ) {
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
        if (maxEdge <= MAX_EDGE) return copy(Bitmap.Config.ARGB_8888, true)
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

    private fun FaceDetectionResult.scaledTo(sx: Float, sy: Float) = copy(
        boundingBox = RectF(
            boundingBox.left * sx, boundingBox.top * sy,
            boundingBox.right * sx, boundingBox.bottom * sy
        ),
        landmarks      = landmarks.map { PointF(it.x * sx, it.y * sy) },
        faceOutline    = faceOutline.map { PointF(it.x * sx, it.y * sy) },
        leftEyeCenter  = PointF(leftEyeCenter.x * sx,  leftEyeCenter.y * sy),
        rightEyeCenter = PointF(rightEyeCenter.x * sx, rightEyeCenter.y * sy),
        noseTip        = PointF(noseTip.x * sx, noseTip.y * sy),
        mouthCenter    = PointF(mouthCenter.x * sx, mouthCenter.y * sy)
    )

    private fun weighted(step: MorphStep, fraction: Float): Float {
        var acc = 0f
        for (s in MorphStep.values()) {
            if (s == step) { acc += s.weight * fraction; break }
            acc += s.weight
        }
        return acc.coerceIn(0f, 1f)
    }
}

data class EngineMorphProgress(
    val step: MorphStep,
    val stepProgress: Float,
    val overallProgress: Float,
    val frameIndex: Int = 0,
    val totalFrames: Int = 1,
    val result: Bitmap? = null
)
