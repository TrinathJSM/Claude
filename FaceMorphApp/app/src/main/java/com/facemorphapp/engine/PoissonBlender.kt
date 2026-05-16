package com.facemorphapp.engine

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.min

/**
 * Poisson seamless cloning — polygon-masked pixel composite.
 *
 * GPU path: Jacobi solver (50 iterations) on a 256×256 downsampled ROI,
 * then upsample and composite via scanline polygon mask with feathered edges.
 *
 * CPU path: direct scanline alpha composite with feathering — no Jacobi solve.
 *
 * Key correctness constraint: ONLY pixels strictly inside the face-polygon mask
 * are written to the output. The bounding-box region outside the polygon is
 * left as original target pixels, eliminating the "black rectangle" artefact
 * produced by compositing the full ROI rectangle.
 */
object PoissonBlender {

    private const val JACOBI_ITERATIONS = 50
    private const val DOWNSAMPLE_SIZE   = 256
    private const val FEATHER_GPU       = 8   // px blend band for GPU path
    private const val FEATHER_CPU       = 16  // px blend band for CPU path

    // ── Public API ─────────────────────────────────────────────────────────────

    fun blend(
        targetBitmap: Bitmap,
        sourcePatch:  Bitmap,
        maskPoints:   List<PointF>
    ): Bitmap {
        val result = targetBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (maskPoints.size < 3) return result

        val minX = max(0, maskPoints.minOf { it.x }.toInt())
        val minY = max(0, maskPoints.minOf { it.y }.toInt())
        val maxX = min(result.width  - 1, maskPoints.maxOf { it.x }.toInt())
        val maxY = min(result.height - 1, maskPoints.maxOf { it.y }.toInt())
        val roiW = maxX - minX + 1
        val roiH = maxY - minY + 1
        if (roiW <= 0 || roiH <= 0) return result

        val scale  = DOWNSAMPLE_SIZE.toFloat() / max(roiW, roiH).toFloat()
        val solveW = max(1, (roiW * scale).toInt())
        val solveH = max(1, (roiH * scale).toInt())

        val srcRoi   = extractRoi(sourcePatch, minX, minY, roiW, roiH)
        val tgtRoi   = extractRoi(result,      minX, minY, roiW, roiH)
        val srcSmall = Bitmap.createScaledBitmap(srcRoi,  solveW, solveH, true)
        val tgtSmall = Bitmap.createScaledBitmap(tgtRoi,  solveW, solveH, true)
        val mask     = buildMask(maskPoints, minX, minY, roiW, roiH, solveW, solveH)
        val solved   = jacobiSolve(srcSmall, tgtSmall, mask, JACOBI_ITERATIONS)
        val blended  = Bitmap.createScaledBitmap(solved, roiW, roiH, true)

        compositePolygon(result, blended, maskPoints, minX, minY, roiW, roiH)

        srcRoi.recycle(); tgtRoi.recycle()
        srcSmall.recycle(); tgtSmall.recycle()
        solved.recycle(); blended.recycle()
        return result
    }

    fun blendFeathered(
        targetBitmap: Bitmap,
        sourcePatch:  Bitmap,
        maskPoints:   List<PointF>
    ): Bitmap {
        val result = targetBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (maskPoints.size < 3) return result

        val w = result.width; val h = result.height
        val tgtPx = IntArray(w * h); result.getPixels(tgtPx,     0, w, 0, 0, w, h)
        val srcPx = IntArray(w * h); sourcePatch.getPixels(srcPx, 0, w, 0, 0, w, h)
        val outPx = tgtPx.copyOf()
        val n     = maskPoints.size

        for (py in 0 until h) {
            val xs = scanlineIntersections(maskPoints, n, py.toFloat())
            var j = 0
            while (j + 1 < xs.size) {
                val fillLeft  = xs[j]
                val fillRight = xs[j + 1]
                val ix0 = max(0,     fillLeft.toInt())
                val ix1 = min(w - 1, fillRight.toInt())
                for (px in ix0..ix1) {
                    val idx   = py * w + px
                    val dist  = minOf(px - fillLeft, fillRight - px).coerceAtLeast(0f)
                    val alpha = if (dist >= FEATHER_CPU) 1f else dist / FEATHER_CPU
                    outPx[idx] = blendPixel(tgtPx[idx], srcPx[idx], alpha)
                }
                j += 2
            }
        }
        result.setPixels(outPx, 0, w, 0, 0, w, h)
        return result
    }

    // ── Polygon composite ──────────────────────────────────────────────────────

    /**
     * Copies [blended] (roiW×roiH Jacobi result) into [dst] using scanline fill of [maskPts].
     * Only pixels inside the polygon are written; bounding-box pixels outside the polygon
     * stay as original target content, preventing the rectangular-border artefact.
     */
    private fun compositePolygon(
        dst: Bitmap, blended: Bitmap, maskPts: List<PointF>,
        rx: Int, ry: Int, rw: Int, rh: Int
    ) {
        val dstW  = dst.width
        val dstH  = dst.height
        val dstPx = IntArray(dstW * dstH)
        val blPx  = IntArray(rw * rh)
        dst.getPixels(dstPx,  0, dstW, 0, 0, dstW, dstH)
        blended.getPixels(blPx, 0, rw,  0, 0, rw,  rh)

        val localPts = maskPts.map { PointF(it.x - rx, it.y - ry) }
        val n        = localPts.size

        for (py in 0 until rh) {
            val sy = ry + py
            if (sy < 0 || sy >= dstH) continue
            val xs = scanlineIntersections(localPts, n, py.toFloat())
            var j  = 0
            while (j + 1 < xs.size) {
                val fillLeft  = xs[j]
                val fillRight = xs[j + 1]
                val ix0 = max(0,      fillLeft.toInt())
                val ix1 = min(rw - 1, fillRight.toInt())
                for (px in ix0..ix1) {
                    val sx = rx + px
                    if (sx < 0 || sx >= dstW) continue
                    val di    = sy * dstW + sx
                    val si    = py * rw   + px
                    val dist  = minOf(px - fillLeft, fillRight - px).coerceAtLeast(0f)
                    val alpha = if (dist >= FEATHER_GPU) 1f else dist / FEATHER_GPU
                    dstPx[di] = blendPixel(dstPx[di], blPx[si], alpha)
                }
                j += 2
            }
        }
        dst.setPixels(dstPx, 0, dstW, 0, 0, dstW, dstH)
    }

    // ── Shared helpers ─────────────────────────────────────────────────────────

    private fun scanlineIntersections(pts: List<PointF>, n: Int, py: Float): List<Float> {
        val xs = mutableListOf<Float>()
        for (i in 0 until n) {
            val p1 = pts[i]; val p2 = pts[(i + 1) % n]
            if ((p1.y <= py && p2.y > py) || (p2.y <= py && p1.y > py)) {
                xs.add(p1.x + (py - p1.y) / (p2.y - p1.y) * (p2.x - p1.x))
            }
        }
        xs.sort()
        return xs
    }

    private fun blendPixel(dst: Int, src: Int, alpha: Float): Int {
        val ia = 1f - alpha
        val r  = ((dst shr 16 and 0xFF) * ia + (src shr 16 and 0xFF) * alpha).toInt().coerceIn(0, 255)
        val g  = ((dst shr 8  and 0xFF) * ia + (src shr 8  and 0xFF) * alpha).toInt().coerceIn(0, 255)
        val b  = ((dst        and 0xFF) * ia + (src        and 0xFF) * alpha).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun extractRoi(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
        val safeW = min(w, src.width  - x)
        val safeH = min(h, src.height - y)
        return Bitmap.createBitmap(src, x, y, safeW, safeH)
    }

    private fun buildMask(
        maskPoints: List<PointF>,
        minX: Int, minY: Int, roiW: Int, roiH: Int,
        solveW: Int, solveH: Int
    ): BooleanArray {
        val scaleX    = solveW.toFloat() / roiW
        val scaleY    = solveH.toFloat() / roiH
        val scaledPts = maskPoints.map { PointF((it.x - minX) * scaleX, (it.y - minY) * scaleY) }
        val mask      = BooleanArray(solveW * solveH)
        for (py in 0 until solveH) {
            val xs = scanlineIntersections(scaledPts, scaledPts.size, py.toFloat())
            var i  = 0
            while (i + 1 < xs.size) {
                val x0 = max(0,          xs[i].toInt())
                val x1 = min(solveW - 1, xs[i + 1].toInt())
                for (px in x0..x1) mask[py * solveW + px] = true
                i += 2
            }
        }
        return mask
    }

    // ── Jacobi solver ──────────────────────────────────────────────────────────

    private fun jacobiSolve(
        src: Bitmap, target: Bitmap, mask: BooleanArray, iterations: Int
    ): Bitmap {
        val w = src.width; val h = src.height
        val srcPx = IntArray(w * h); src.getPixels(srcPx,    0, w, 0, 0, w, h)
        val tgtPx = IntArray(w * h); target.getPixels(tgtPx, 0, w, 0, 0, w, h)

        val rCur = FloatArray(w * h); val gCur = FloatArray(w * h); val bCur = FloatArray(w * h)
        val rNxt = FloatArray(w * h); val gNxt = FloatArray(w * h); val bNxt = FloatArray(w * h)
        for (i in 0 until w * h) {
            rCur[i] = (tgtPx[i] shr 16 and 0xFF).toFloat()
            gCur[i] = (tgtPx[i] shr 8  and 0xFF).toFloat()
            bCur[i] = (tgtPx[i]        and 0xFF).toFloat()
        }

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
                rNxt[idx] = clamp8((rCur[idx-1] + rCur[idx+1] + rCur[idx-w] + rCur[idx+w] + lapR[idx]) / 4f)
                gNxt[idx] = clamp8((gCur[idx-1] + gCur[idx+1] + gCur[idx-w] + gCur[idx+w] + lapG[idx]) / 4f)
                bNxt[idx] = clamp8((bCur[idx-1] + bCur[idx+1] + bCur[idx-w] + bCur[idx+w] + lapB[idx]) / 4f)
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

    private inline fun laplacian(px: IntArray, idx: Int, w: Int, ch: (Int) -> Float): Float =
        ch(px[idx-1]) + ch(px[idx+1]) + ch(px[idx-w]) + ch(px[idx+w]) - 4f * ch(px[idx])

    private fun clamp8(v: Float): Float = max(0f, min(255f, v))
}
