package com.facemorphapp.engine

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Per-channel mean/stddev color transfer (Reinhard et al., 2001).
 *
 * For each R/G/B channel independently, computes the mean and standard deviation
 * of [source] and [reference] pixels within the face mask polygon, then applies
 * the linear transform:
 *
 *   corrected(x) = (source(x) − srcMean) × (refStd / srcStd) + refMean
 *
 * This matches the skin-tone distribution of the morphed result to the target face,
 * eliminating residual hue/brightness mismatches after blending.
 */
object ColorCorrector {

    fun correct(source: Bitmap, reference: Bitmap, maskPoints: List<PointF>): Bitmap {
        val w = source.width; val h = source.height
        if (maskPoints.size < 3 || w != reference.width || h != reference.height) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val srcPx = IntArray(w * h)
        val refPx = IntArray(w * h)
        source.getPixels(srcPx, 0, w, 0, 0, w, h)
        reference.getPixels(refPx, 0, w, 0, 0, w, h)

        val mask = buildScanlineMask(maskPoints, w, h)

        val srcMeans = FloatArray(3)
        val refMeans = FloatArray(3)
        var count = 0
        for (i in srcPx.indices) {
            if (!mask[i]) continue
            srcMeans[0] += (srcPx[i] shr 16 and 0xFF).toFloat()
            srcMeans[1] += (srcPx[i] shr 8  and 0xFF).toFloat()
            srcMeans[2] += (srcPx[i]        and 0xFF).toFloat()
            refMeans[0] += (refPx[i] shr 16 and 0xFF).toFloat()
            refMeans[1] += (refPx[i] shr 8  and 0xFF).toFloat()
            refMeans[2] += (refPx[i]        and 0xFF).toFloat()
            count++
        }
        if (count == 0) return source.copy(Bitmap.Config.ARGB_8888, true)

        for (c in 0..2) { srcMeans[c] /= count; refMeans[c] /= count }

        val srcVars = FloatArray(3)
        val refVars = FloatArray(3)
        for (i in srcPx.indices) {
            if (!mask[i]) continue
            val ds0 = (srcPx[i] shr 16 and 0xFF) - srcMeans[0]
            val ds1 = (srcPx[i] shr 8  and 0xFF) - srcMeans[1]
            val ds2 = (srcPx[i]        and 0xFF) - srcMeans[2]
            val dr0 = (refPx[i] shr 16 and 0xFF) - refMeans[0]
            val dr1 = (refPx[i] shr 8  and 0xFF) - refMeans[1]
            val dr2 = (refPx[i]        and 0xFF) - refMeans[2]
            srcVars[0] += ds0 * ds0; srcVars[1] += ds1 * ds1; srcVars[2] += ds2 * ds2
            refVars[0] += dr0 * dr0; refVars[1] += dr1 * dr1; refVars[2] += dr2 * dr2
        }

        val ratios = FloatArray(3) { c ->
            val srcStd = sqrt(srcVars[c] / count)
            val refStd = sqrt(refVars[c] / count)
            if (srcStd < 1f) 1f else refStd / srcStd
        }

        val result = srcPx.copyOf()
        for (i in srcPx.indices) {
            if (!mask[i]) continue
            val a = srcPx[i] ushr 24
            val r = ((srcPx[i] shr 16 and 0xFF) - srcMeans[0]) * ratios[0] + refMeans[0]
            val g = ((srcPx[i] shr 8  and 0xFF) - srcMeans[1]) * ratios[1] + refMeans[1]
            val b = ((srcPx[i]        and 0xFF) - srcMeans[2]) * ratios[2] + refMeans[2]
            result[i] = (a shl 24) or
                (r.coerceIn(0f, 255f).toInt() shl 16) or
                (g.coerceIn(0f, 255f).toInt() shl 8) or
                b.coerceIn(0f, 255f).toInt()
        }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(result, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun buildScanlineMask(pts: List<PointF>, w: Int, h: Int): BooleanArray {
        val mask = BooleanArray(w * h)
        val n = pts.size
        for (py in 0 until h) {
            val xs = mutableListOf<Float>()
            for (i in 0 until n) {
                val p1 = pts[i]; val p2 = pts[(i + 1) % n]
                if ((p1.y <= py && p2.y > py) || (p2.y <= py && p1.y > py)) {
                    xs.add(p1.x + (py - p1.y) / (p2.y - p1.y) * (p2.x - p1.x))
                }
            }
            xs.sort()
            var j = 0
            while (j + 1 < xs.size) {
                val x0 = max(0, xs[j].toInt())
                val x1 = min(w - 1, xs[j + 1].toInt())
                for (px in x0..x1) mask[py * w + px] = true
                j += 2
            }
        }
        return mask
    }
}
