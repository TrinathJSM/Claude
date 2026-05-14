package com.facemorphapp.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Poisson seamless cloning approximation.
 *
 * A full sparse linear system solve is prohibitively slow on mobile for large images.
 * Instead we use a two-pass approach:
 *  1. Iterative Jacobi solver (~20 iterations) on a downsampled ROI.
 *  2. Upsample and composite via a feathered alpha mask for speed on lower-end devices.
 *
 * For API 31+ we fall back to a pure Kotlin convolution path (RenderScript was
 * deprecated in API 31 and removed in API 33).
 *
 * This gives perceptually seamless results for typical face-swap use cases without
 * requiring native code or GPU compute shaders.
 */
object PoissonBlender {

    private const val JACOBI_ITERATIONS = 20
    private const val DOWNSAMPLE_SIZE = 256  // solver runs on 256×256 max

    /**
     * Blends [sourcePatch] (the warped face) into [targetBitmap] within the region
     * defined by [maskPoints] (the face boundary polygon).
     *
     * Returns a new mutable [Bitmap] with the result composited onto [targetBitmap].
     */
    fun blend(
        targetBitmap: Bitmap,
        sourcePatch: Bitmap,
        maskPoints: List<PointF>
    ): Bitmap {
        val result = targetBitmap.copy(Bitmap.Config.ARGB_8888, true)

        if (maskPoints.size < 3) {
            // Nothing to blend
            val canvas = Canvas(result)
            canvas.drawBitmap(sourcePatch, 0f, 0f, null)
            return result
        }

        // Build a tight bounding box around the mask polygon
        val minX = max(0, maskPoints.minOf { it.x }.toInt())
        val minY = max(0, maskPoints.minOf { it.y }.toInt())
        val maxX = min(result.width - 1, maskPoints.maxOf { it.x }.toInt())
        val maxY = min(result.height - 1, maskPoints.maxOf { it.y }.toInt())
        val roiW = maxX - minX + 1
        val roiH = maxY - minY + 1
        if (roiW <= 0 || roiH <= 0) return result

        // Downsample factor for the Poisson solve
        val scale = DOWNSAMPLE_SIZE.toFloat() / max(roiW, roiH).toFloat()
        val solveW = max(1, (roiW * scale).toInt())
        val solveH = max(1, (roiH * scale).toInt())

        val srcRoi = extractRoi(sourcePatch, minX, minY, roiW, roiH)
        val tgtRoi = extractRoi(result, minX, minY, roiW, roiH)

        val srcSmall = Bitmap.createScaledBitmap(srcRoi, solveW, solveH, true)
        val tgtSmall = Bitmap.createScaledBitmap(tgtRoi, solveW, solveH, true)

        // Build binary mask at solve resolution
        val mask = buildMask(maskPoints, minX, minY, roiW, roiH, solveW, solveH)

        // Run Jacobi solver
        val solved = jacobiSolve(srcSmall, tgtSmall, mask, JACOBI_ITERATIONS)

        // Upsample solved region back to ROI size
        val blended = Bitmap.createScaledBitmap(solved, roiW, roiH, true)

        // Composite onto result using feathered mask for smooth edges
        compositeWithFeather(result, blended, maskPoints, minX, minY, roiW, roiH)

        srcRoi.recycle(); tgtRoi.recycle(); srcSmall.recycle()
        tgtSmall.recycle(); solved.recycle(); blended.recycle()

        return result
    }

    // ── Feathered alpha-gradient fallback ──────────────────────────────────────

    /**
     * Faster fallback: composite source patch onto target using a radial feather
     * around the face centroid.  No linear solve — used when GPU is unavailable.
     */
    fun blendFeathered(
        targetBitmap: Bitmap,
        sourcePatch: Bitmap,
        maskPoints: List<PointF>
    ): Bitmap {
        val result = targetBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (maskPoints.size < 3) return result

        val cx = maskPoints.sumOf { it.x.toDouble() }.toFloat() / maskPoints.size
        val cy = maskPoints.sumOf { it.y.toDouble() }.toFloat() / maskPoints.size
        val radius = maskPoints.maxOf { sqrt(((it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy)).toDouble()).toFloat() }

        val maskBitmap = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        Canvas(maskBitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, radius, intArrayOf(0xFF_FFFFFF.toInt(), 0x00_FFFFFF), null, Shader.TileMode.CLAMP)
            }
            val path = Path().apply {
                moveTo(maskPoints[0].x, maskPoints[0].y)
                maskPoints.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, paint)
        }

        Canvas(result).apply {
            val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
            drawBitmap(maskBitmap, 0f, 0f, null)
            drawBitmap(sourcePatch, 0f, 0f, paint)
        }
        maskBitmap.recycle()
        return result
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun extractRoi(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
        val safeW = min(w, src.width - x)
        val safeH = min(h, src.height - y)
        return Bitmap.createBitmap(src, x, y, safeW, safeH)
    }

    private fun buildMask(
        maskPoints: List<PointF>,
        minX: Int, minY: Int, roiW: Int, roiH: Int,
        solveW: Int, solveH: Int
    ): BooleanArray {
        val scaleX = solveW.toFloat() / roiW
        val scaleY = solveH.toFloat() / roiH
        val scaledPts = maskPoints.map {
            PointF((it.x - minX) * scaleX, (it.y - minY) * scaleY)
        }
        val mask = BooleanArray(solveW * solveH)
        // Scanline fill
        for (py in 0 until solveH) {
            val intersections = mutableListOf<Float>()
            val n = scaledPts.size
            for (i in 0 until n) {
                val p1 = scaledPts[i]; val p2 = scaledPts[(i + 1) % n]
                if ((p1.y <= py && p2.y > py) || (p2.y <= py && p1.y > py)) {
                    val x = p1.x + (py - p1.y) / (p2.y - p1.y) * (p2.x - p1.x)
                    intersections.add(x)
                }
            }
            intersections.sort()
            var i = 0
            while (i + 1 < intersections.size) {
                val x0 = max(0, intersections[i].toInt())
                val x1 = min(solveW - 1, intersections[i + 1].toInt())
                for (px in x0..x1) mask[py * solveW + px] = true
                i += 2
            }
        }
        return mask
    }

    /**
     * Jacobi iteration: for each interior masked pixel, the solved value is the
     * average of its 4-neighbors' current values plus the Laplacian of the source.
     * This minimises the Dirichlet energy (Poisson equation) subject to Dirichlet
     * boundary conditions taken from [target].
     */
    private fun jacobiSolve(
        src: Bitmap, target: Bitmap, mask: BooleanArray, iterations: Int
    ): Bitmap {
        val w = src.width; val h = src.height
        val srcPx = IntArray(w * h); src.getPixels(srcPx, 0, w, 0, 0, w, h)
        val tgtPx = IntArray(w * h); target.getPixels(tgtPx, 0, w, 0, 0, w, h)

        val rCur = FloatArray(w * h); val gCur = FloatArray(w * h); val bCur = FloatArray(w * h)
        val rNxt = FloatArray(w * h); val gNxt = FloatArray(w * h); val bNxt = FloatArray(w * h)

        // Initialise with target (boundary conditions propagate inward)
        for (i in 0 until w * h) {
            val c = tgtPx[i]
            rCur[i] = (c shr 16 and 0xFF).toFloat()
            gCur[i] = (c shr 8  and 0xFF).toFloat()
            bCur[i] = (c        and 0xFF).toFloat()
        }

        // Precompute source Laplacian (guidance field)
        val lapR = FloatArray(w * h); val lapG = FloatArray(w * h); val lapB = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val idx = y * w + x
            lapR[idx] = laplacian(srcPx, idx, w) { (it shr 16 and 0xFF).toFloat() }
            lapG[idx] = laplacian(srcPx, idx, w) { (it shr 8  and 0xFF).toFloat() }
            lapB[idx] = laplacian(srcPx, idx, w) { (it        and 0xFF).toFloat() }
        }

        repeat(iterations) {
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val idx = y * w + x
                if (!mask[idx]) {
                    rNxt[idx] = rCur[idx]; gNxt[idx] = gCur[idx]; bNxt[idx] = bCur[idx]
                    continue
                }
                rNxt[idx] = clamp8((rCur[idx - 1] + rCur[idx + 1] + rCur[idx - w] + rCur[idx + w] + lapR[idx]) / 4f)
                gNxt[idx] = clamp8((gCur[idx - 1] + gCur[idx + 1] + gCur[idx - w] + gCur[idx + w] + lapG[idx]) / 4f)
                bNxt[idx] = clamp8((bCur[idx - 1] + bCur[idx + 1] + bCur[idx - w] + bCur[idx + w] + lapB[idx]) / 4f)
            }
            rNxt.copyInto(rCur); gNxt.copyInto(gCur); bNxt.copyInto(bCur)
        }

        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            val a = tgtPx[i] ushr 24
            out[i] = (a shl 24) or (rCur[i].toInt() shl 16) or (gCur[i].toInt() shl 8) or bCur[i].toInt()
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    private inline fun laplacian(px: IntArray, idx: Int, w: Int, ch: (Int) -> Float): Float {
        return ch(px[idx - 1]) + ch(px[idx + 1]) + ch(px[idx - w]) + ch(px[idx + w]) - 4f * ch(px[idx])
    }

    private fun clamp8(v: Float): Float = max(0f, min(255f, v))

    private fun compositeWithFeather(
        dst: Bitmap, blended: Bitmap, maskPts: List<PointF>,
        rx: Int, ry: Int, rw: Int, rh: Int
    ) {
        val tmp = Bitmap.createBitmap(dst.width, dst.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tmp)
        canvas.drawBitmap(dst, 0f, 0f, null)

        val patch = Bitmap.createBitmap(dst.width, dst.height, Bitmap.Config.ARGB_8888)
        Canvas(patch).drawBitmap(blended, rx.toFloat(), ry.toFloat(), null)

        val maskBmp = Bitmap.createBitmap(dst.width, dst.height, Bitmap.Config.ARGB_8888)
        Canvas(maskBmp).apply {
            val path = Path().apply {
                moveTo(maskPts[0].x, maskPts[0].y)
                maskPts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1 })
        }

        Canvas(dst).apply {
            drawBitmap(tmp, 0f, 0f, null)
            val p = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
            drawBitmap(maskBmp, 0f, 0f, null)
            drawBitmap(patch, 0f, 0f, p)
        }

        tmp.recycle(); patch.recycle(); maskBmp.recycle()
    }
}
