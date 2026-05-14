package com.facemorphapp.engine

import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DelaunayTriangulator].
 *
 * Verifies the Bowyer–Watson implementation satisfies the core Delaunay
 * property (circumcircle criterion) and structural invariants.
 */
class DelaunayTriangulatorTest {

    // ── Basic structural tests ────────────────────────────────────────────────

    @Test
    fun `three points produce exactly one triangle`() {
        val pts = listOf(PointF(0f, 0f), PointF(100f, 0f), PointF(50f, 100f))
        val tris = DelaunayTriangulator.triangulate(pts)
        assertEquals("Three collinear-free points must yield exactly 1 triangle", 1, tris.size)
    }

    @Test
    fun `four points in a square produce two triangles`() {
        val pts = listOf(
            PointF(0f, 0f), PointF(100f, 0f),
            PointF(100f, 100f), PointF(0f, 100f)
        )
        val tris = DelaunayTriangulator.triangulate(pts)
        assertEquals("A square (4 points) must produce exactly 2 triangles", 2, tris.size)
    }

    @Test
    fun `all triangle vertex indices are within input range`() {
        val pts = randomGrid(5, 5, spacing = 50f)
        val tris = DelaunayTriangulator.triangulate(pts)
        for (t in tris) {
            assertTrue(t.a in pts.indices)
            assertTrue(t.b in pts.indices)
            assertTrue(t.c in pts.indices)
        }
    }

    @Test
    fun `no degenerate triangles with zero area`() {
        val pts = randomGrid(4, 4, spacing = 40f)
        val tris = DelaunayTriangulator.triangulate(pts)
        for (t in tris) {
            val area = triangleArea(pts[t.a], pts[t.b], pts[t.c])
            assertTrue("Triangle ${t} has near-zero area $area", area > 0.1f)
        }
    }

    @Test
    fun `empty list returns empty triangulation`() {
        assertEquals(0, DelaunayTriangulator.triangulate(emptyList()).size)
    }

    @Test
    fun `two points returns empty triangulation`() {
        assertEquals(0, DelaunayTriangulator.triangulate(listOf(PointF(0f, 0f), PointF(1f, 1f))).size)
    }

    // ── Delaunay circumcircle property ────────────────────────────────────────

    @Test
    fun `no point lies strictly inside any triangle circumcircle`() {
        val pts = randomGrid(4, 4, spacing = 30f)
        val tris = DelaunayTriangulator.triangulate(pts)
        for (t in tris) {
            val a = pts[t.a]; val b = pts[t.b]; val c = pts[t.c]
            for (i in pts.indices) {
                if (i == t.a || i == t.b || i == t.c) continue
                assertFalse(
                    "Point $i lies inside circumcircle of triangle $t — Delaunay violated",
                    strictlyInCircumcircle(a, b, c, pts[i])
                )
            }
        }
    }

    // ── Convex hull ───────────────────────────────────────────────────────────

    @Test
    fun `convex hull of a regular polygon has all n vertices`() {
        val n = 8
        val pts = (0 until n).map { i ->
            val angle = 2 * Math.PI * i / n
            PointF((100 * Math.cos(angle)).toFloat(), (100 * Math.sin(angle)).toFloat())
        }
        val hull = DelaunayTriangulator.convexHullIndices(pts)
        assertEquals("All vertices of a convex polygon should be on the hull", n, hull.size)
    }

    @Test
    fun `interior points excluded from convex hull`() {
        val outer = listOf(PointF(-100f,-100f), PointF(100f,-100f), PointF(100f,100f), PointF(-100f,100f))
        val inner = listOf(PointF(0f,0f), PointF(10f,10f))  // strictly inside
        val all = outer + inner
        val hull = DelaunayTriangulator.convexHullIndices(all)
        // Hull indices should all point to outer vertices (indices 0–3)
        val hullSet = hull.toSet()
        for (innerIdx in 4..5) assertFalse("Inner point $innerIdx should not be on hull", innerIdx in hullSet)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun randomGrid(rows: Int, cols: Int, spacing: Float): List<PointF> {
        val pts = mutableListOf<PointF>()
        for (r in 0 until rows) for (c in 0 until cols) {
            pts.add(PointF(c * spacing, r * spacing))
        }
        return pts
    }

    private fun triangleArea(a: PointF, b: PointF, c: PointF): Float =
        kotlin.math.abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2f

    /** Returns true only if p is STRICTLY inside the circumcircle of (a,b,c). */
    private fun strictlyInCircumcircle(a: PointF, b: PointF, c: PointF, p: PointF): Boolean {
        val ax = a.x - p.x; val ay = a.y - p.y
        val bx = b.x - p.x; val by = b.y - p.y
        val cx = c.x - p.x; val cy = c.y - p.y
        val det = ax * (by * (cx*cx + cy*cy) - cy * (bx*bx + by*by)) -
                  ay * (bx * (cx*cx + cy*cy) - cx * (bx*bx + by*by)) +
                  (ax*ax + ay*ay) * (bx*cy - by*cx)
        return det > 1e-4f  // strict: ignores numerical noise on circumcircle boundary
    }
}
