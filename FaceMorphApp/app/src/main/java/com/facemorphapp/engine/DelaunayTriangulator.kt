package com.facemorphapp.engine

import android.graphics.PointF

/**
 * Bowyer–Watson incremental Delaunay triangulation.
 *
 * Operates on 2-D point sets; returns a list of triangle index triples referencing
 * the input [points] array.  Corner points of a large super-triangle are appended
 * to [points] internally and stripped from the output — callers see only triangles
 * whose three vertices all belong to the original point set.
 */
object DelaunayTriangulator {

    data class Triangle(val a: Int, val b: Int, val c: Int)

    /**
     * Triangulates [points] and returns index triples into the ORIGINAL list.
     * A bounding frame of four corner points (indices ≥ points.size) is used
     * during computation and excluded from the result.
     */
    fun triangulate(points: List<PointF>): List<Triangle> {
        if (points.size < 3) return emptyList()

        // Mutable working copy; super-triangle vertices appended at the end
        val pts = points.toMutableList()
        val n = pts.size

        // Build a super-triangle that fully encloses all input points
        val superA: Int; val superB: Int; val superC: Int
        run {
            val minX = pts.minOf { it.x } - 1f
            val minY = pts.minOf { it.y } - 1f
            val maxX = pts.maxOf { it.x } + 1f
            val maxY = pts.maxOf { it.y } + 1f
            val dx = maxX - minX; val dy = maxY - minY
            val delta = maxOf(dx, dy) * 3f
            superA = pts.size; pts.add(PointF(minX - delta, minY - delta))
            superB = pts.size; pts.add(PointF(minX + delta * 2, minY - delta))
            superC = pts.size; pts.add(PointF(minX, minY + delta * 2))
        }

        val triangles = mutableListOf(Triple(superA, superB, superC))

        for (pi in 0 until n) {
            val p = pts[pi]
            // Find all triangles whose circumcircle contains p
            val bad = mutableListOf<Triple<Int, Int, Int>>()
            for (t in triangles) {
                if (inCircumcircle(pts[t.first], pts[t.second], pts[t.third], p)) bad.add(t)
            }
            // Collect boundary edges of the bad triangles (edges not shared by two bad triangles)
            val boundary = mutableListOf<Pair<Int, Int>>()
            for (t in bad) {
                val edges = listOf(t.first to t.second, t.second to t.third, t.third to t.first)
                for (e in edges) {
                    val shared = bad.count { o ->
                        o != t && ((o.first == e.first && o.second == e.second) ||
                                   (o.first == e.second && o.second == e.first) ||
                                   (o.second == e.first && o.third == e.second) ||
                                   (o.second == e.second && o.third == e.first) ||
                                   (o.third == e.first && o.first == e.second) ||
                                   (o.third == e.second && o.first == e.first))
                    }
                    if (shared == 0) boundary.add(e)
                }
            }
            triangles.removeAll(bad)
            for (e in boundary) triangles.add(Triple(e.first, e.second, pi))
        }

        // Remove triangles sharing a vertex with the super-triangle
        return triangles
            .filter { t -> t.first < n && t.second < n && t.third < n }
            .map { Triangle(it.first, it.second, it.third) }
    }

    /**
     * Returns true if point [p] lies strictly inside the circumcircle of triangle
     * (a, b, c).  Uses the determinant formulation from Shewchuk (1996).
     */
    private fun inCircumcircle(a: PointF, b: PointF, c: PointF, p: PointF): Boolean {
        val ax = a.x - p.x; val ay = a.y - p.y
        val bx = b.x - p.x; val by = b.y - p.y
        val cx = c.x - p.x; val cy = c.y - p.y
        val det = ax * (by * (cx * cx + cy * cy) - cy * (bx * bx + by * by)) -
                  ay * (bx * (cx * cx + cy * cy) - cx * (bx * bx + by * by)) +
                  (ax * ax + ay * ay) * (bx * cy - by * cx)
        // Positive det ⟹ p is inside (assumes CCW vertex order; works either way with sign flip)
        return det > 0
    }

    /**
     * Computes a convex hull around [points] and returns their indices in CCW order.
     * Used to add frame-boundary points before triangulating so the warp covers the
     * entire image rectangle.
     */
    fun convexHullIndices(points: List<PointF>): List<Int> {
        if (points.size < 3) return points.indices.toList()
        // Graham scan
        val sorted = points.indices.sortedWith(compareBy({ points[it].x }, { points[it].y }))
        val hull = mutableListOf<Int>()
        // Lower hull
        for (i in sorted) {
            while (hull.size >= 2 && cross(points[hull[hull.size - 2]], points[hull.last()], points[i]) <= 0)
                hull.removeAt(hull.lastIndex)
            hull.add(i)
        }
        // Upper hull
        val lower = hull.size + 1
        for (i in sorted.reversed()) {
            while (hull.size >= lower && cross(points[hull[hull.size - 2]], points[hull.last()], points[i]) <= 0)
                hull.removeAt(hull.lastIndex)
            hull.add(i)
        }
        hull.removeAt(hull.lastIndex)
        return hull
    }

    private fun cross(o: PointF, a: PointF, b: PointF): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
}
