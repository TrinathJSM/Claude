package com.facemorphapp.engine

import android.graphics.Matrix
import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit tests for [AffineTransformer].
 *
 * Robolectric/Android runtime not required — Android Matrix arithmetic is
 * deterministic and testable with pure JVM (Matrix is not mocked here; the
 * tests verify the mathematical properties independently, then confirm the
 * Matrix values match).
 */
class AffineTransformerTest {

    private val eps = 1e-3f  // floating-point tolerance

    // ── Identity transform ────────────────────────────────────────────────────

    @Test
    fun `identity when source and destination points are equal`() {
        val pts = listOf(
            PointF(100f, 200f),
            PointF(300f, 200f),
            PointF(200f, 350f),
            PointF(200f, 400f)
        )
        val matrix = AffineTransformer.computeSimilarityMatrix(
            pts[0], pts[1], pts[2], pts[3],
            pts[0], pts[1], pts[2], pts[3]
        )
        // The matrix should be very close to identity
        val vals = FloatArray(9); matrix.getValues(vals)
        assertNear(1f, vals[Matrix.MSCALE_X])
        assertNear(0f, vals[Matrix.MSKEW_X])
        assertNear(0f, vals[Matrix.MTRANS_X])
        assertNear(0f, vals[Matrix.MSKEW_Y])
        assertNear(1f, vals[Matrix.MSCALE_Y])
        assertNear(0f, vals[Matrix.MTRANS_Y])
    }

    // ── Pure translation ──────────────────────────────────────────────────────

    @Test
    fun `pure translation — scale=1 rotation=0`() {
        val tx = 50f; val ty = -30f
        val src = listOf(PointF(0f, 0f), PointF(100f, 0f), PointF(50f, 80f), PointF(50f, 120f))
        val dst = src.map { PointF(it.x + tx, it.y + ty) }

        val matrix = AffineTransformer.computeSimilarityMatrix(
            src[0], src[1], src[2], src[3],
            dst[0], dst[1], dst[2], dst[3]
        )
        val vals = FloatArray(9); matrix.getValues(vals)
        assertNear(1f,  vals[Matrix.MSCALE_X], 0.01f)
        assertNear(0f,  vals[Matrix.MSKEW_X],  0.01f)
        assertNear(tx,  vals[Matrix.MTRANS_X])
        assertNear(ty,  vals[Matrix.MTRANS_Y])
    }

    // ── Pure uniform scale ────────────────────────────────────────────────────

    @Test
    fun `uniform scale-up by 2x around origin`() {
        val s = 2f
        val src = listOf(PointF(10f, 20f), PointF(30f, 20f), PointF(20f, 40f), PointF(20f, 50f))
        val dst = src.map { PointF(it.x * s, it.y * s) }

        val matrix = AffineTransformer.computeSimilarityMatrix(
            src[0], src[1], src[2], src[3],
            dst[0], dst[1], dst[2], dst[3]
        )
        // Verify mapped points are correct (end-to-end)
        src.zip(dst).forEach { (sp, dp) ->
            val mapped = AffineTransformer.mapPoint(matrix, sp)
            assertNear(dp.x, mapped.x, 1f)
            assertNear(dp.y, mapped.y, 1f)
        }
    }

    // ── Pure 90° rotation ─────────────────────────────────────────────────────

    @Test
    fun `90-degree CCW rotation about centroid`() {
        val cx = 200f; val cy = 200f
        fun rotate90(p: PointF) = PointF(cx - (p.y - cy), cy + (p.x - cx))

        val src = listOf(PointF(150f, 200f), PointF(250f, 200f), PointF(200f, 150f), PointF(200f, 250f))
        val dst = src.map { rotate90(it) }

        val matrix = AffineTransformer.computeSimilarityMatrix(
            src[0], src[1], src[2], src[3],
            dst[0], dst[1], dst[2], dst[3]
        )
        src.zip(dst).forEach { (sp, dp) ->
            val mapped = AffineTransformer.mapPoint(matrix, sp)
            assertNear(dp.x, mapped.x, 2f)
            assertNear(dp.y, mapped.y, 2f)
        }
    }

    // ── mapPoints array API ───────────────────────────────────────────────────

    @Test
    fun `mapPoints transforms flat coordinate array correctly`() {
        val tx = 10f; val ty = 20f
        val src = listOf(PointF(0f, 0f), PointF(100f, 0f), PointF(50f, 80f), PointF(50f, 120f))
        val dst = src.map { PointF(it.x + tx, it.y + ty) }

        val matrix = AffineTransformer.computeSimilarityMatrix(
            src[0], src[1], src[2], src[3],
            dst[0], dst[1], dst[2], dst[3]
        )
        val flatSrc = floatArrayOf(src[0].x, src[0].y, src[1].x, src[1].y)
        val mapped = AffineTransformer.mapPoints(matrix, flatSrc)
        assertNear(dst[0].x, mapped[0])
        assertNear(dst[0].y, mapped[1])
        assertNear(dst[1].x, mapped[2])
        assertNear(dst[1].y, mapped[3])
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = eps) {
        assertTrue(
            "Expected $expected ±$tolerance but was $actual",
            abs(expected - actual) <= tolerance
        )
    }
}
