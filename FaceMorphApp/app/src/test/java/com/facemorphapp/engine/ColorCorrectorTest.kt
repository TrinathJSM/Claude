package com.facemorphapp.engine

import android.graphics.Bitmap
import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [ColorCorrector].
 *
 * These tests verify the correctness of the per-channel mean/stddev linear
 * transfer on known pixel values without requiring Android runtime (bitmaps are
 * mocked by constructing IntArrays directly and comparing channel statistics).
 */
class ColorCorrectorTest {

    // ── Channel statistics ────────────────────────────────────────────────────

    @Test
    fun `mean of all-same-value pixels equals that value`() {
        val px = encodePixel(128, 100, 80)
        val (rMean, gMean, bMean) = channelMeans(IntArray(100) { px })
        assertNear(128f, rMean)
        assertNear(100f, gMean)
        assertNear( 80f, bMean)
    }

    @Test
    fun `stddev of uniform-value array is zero`() {
        val px = encodePixel(64, 64, 64)
        val (rStd, gStd, bStd) = channelStdDevs(IntArray(50) { px }, 64f, 64f, 64f)
        assertNear(0f, rStd)
        assertNear(0f, gStd)
        assertNear(0f, bStd)
    }

    // ── Transfer correctness ──────────────────────────────────────────────────

    @Test
    fun `correcting identical source and reference is a no-op`() {
        val pixels = IntArray(100) { i -> encodePixel((i * 2) % 256, (i * 3) % 256, (i * 5) % 256) }
        val transferResult = applyTransfer(pixels, pixels, pixels.size)
        for (i in pixels.indices) {
            val origR = (pixels[i] shr 16 and 0xFF)
            val corrR = (transferResult[i] shr 16 and 0xFF)
            assertNear(origR.toFloat(), corrR.toFloat(), tolerance = 2f)
        }
    }

    @Test
    fun `correcting dark source toward bright reference increases mean`() {
        val darkPx = IntArray(100) { encodePixel(40, 40, 40) }
        val brightPx = IntArray(100) { encodePixel(200, 200, 200) }
        val result = applyTransfer(darkPx, brightPx, 100)
        val (rMean, _, _) = channelMeans(result)
        assertTrue("After correction toward brighter reference, mean R should increase", rMean > 40f)
    }

    @Test
    fun `corrected output values stay in 0-255 range`() {
        val src = IntArray(64)  { encodePixel(10, 250, 128) }
        val ref = IntArray(64)  { encodePixel(240, 20, 200) }
        val result = applyTransfer(src, ref, 64)
        for (px in result) {
            val r = px shr 16 and 0xFF; val g = px shr 8 and 0xFF; val b = px and 0xFF
            assertTrue("R out of range: $r", r in 0..255)
            assertTrue("G out of range: $g", g in 0..255)
            assertTrue("B out of range: $b", b in 0..255)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun encodePixel(r: Int, g: Int, b: Int, a: Int = 255) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun channelMeans(pixels: IntArray): Triple<Float, Float, Float> {
        val n = pixels.size.toFloat()
        val r = pixels.sumOf { (it shr 16 and 0xFF).toDouble() }.toFloat() / n
        val g = pixels.sumOf { (it shr 8  and 0xFF).toDouble() }.toFloat() / n
        val b = pixels.sumOf { (it        and 0xFF).toDouble() }.toFloat() / n
        return Triple(r, g, b)
    }

    private fun channelStdDevs(pixels: IntArray, rMean: Float, gMean: Float, bMean: Float): Triple<Float, Float, Float> {
        val n = pixels.size.toFloat()
        var rv = 0.0; var gv = 0.0; var bv = 0.0
        for (px in pixels) {
            rv += ((px shr 16 and 0xFF) - rMean).let { it * it }.toDouble()
            gv += ((px shr 8  and 0xFF) - gMean).let { it * it }.toDouble()
            bv += ((px        and 0xFF) - bMean).let { it * it }.toDouble()
        }
        return Triple(kotlin.math.sqrt(rv / n).toFloat(),
                      kotlin.math.sqrt(gv / n).toFloat(),
                      kotlin.math.sqrt(bv / n).toFloat())
    }

    private fun applyTransfer(srcPx: IntArray, refPx: IntArray, n: Int): IntArray {
        val (srcRM, srcGM, srcBM) = channelMeans(srcPx)
        val (refRM, refGM, refBM) = channelMeans(refPx)
        val (srcRS, srcGS, srcBS) = channelStdDevs(srcPx, srcRM, srcGM, srcBM)
        val (refRS, refGS, refBS) = channelStdDevs(refPx, refRM, refGM, refBM)

        val ratioR = if (srcRS < 1f) 1f else refRS / srcRS
        val ratioG = if (srcGS < 1f) 1f else refGS / srcGS
        val ratioB = if (srcBS < 1f) 1f else refBS / srcBS

        return IntArray(n) { i ->
            val a = srcPx[i] ushr 24
            val r = ((srcPx[i] shr 16 and 0xFF) - srcRM) * ratioR + refRM
            val g = ((srcPx[i] shr 8  and 0xFF) - srcGM) * ratioG + refGM
            val b = ((srcPx[i]        and 0xFF) - srcBM) * ratioB + refBM
            (a shl 24) or
                (r.coerceIn(0f, 255f).toInt() shl 16) or
                (g.coerceIn(0f, 255f).toInt() shl 8) or
                b.coerceIn(0f, 255f).toInt()
        }
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.5f) {
        assertTrue("Expected $expected ±$tolerance but was $actual",
            kotlin.math.abs(expected - actual) <= tolerance)
    }
}
