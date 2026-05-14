package com.facemorphapp.engine

import android.graphics.Matrix
import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Computes a 2-D similarity transform (scale + rotation + translation) that maps
 * the source face's four anchor landmarks onto the target face's equivalents.
 *
 * Anchor points: left eye, right eye, nose tip, mouth centre.
 * Using a least-squares fit over these four point pairs gives a stable result even
 * when individual landmarks are noisy.
 */
object AffineTransformer {

    /**
     * Returns an Android [Matrix] that transforms src coordinates → dst coordinates.
     * The matrix encodes scale, rotation, and translation (no shear / perspective).
     */
    fun computeSimilarityMatrix(
        srcLeftEye: PointF,
        srcRightEye: PointF,
        srcNose: PointF,
        srcMouth: PointF,
        dstLeftEye: PointF,
        dstRightEye: PointF,
        dstNose: PointF,
        dstMouth: PointF
    ): Matrix {
        val srcPoints = listOf(srcLeftEye, srcRightEye, srcNose, srcMouth)
        val dstPoints = listOf(dstLeftEye, dstRightEye, dstNose, dstMouth)
        return fitSimilarity(srcPoints, dstPoints)
    }

    /**
     * Least-squares similarity fit (Umeyama method restricted to 2-D).
     * Solves for (scale, cos θ, sin θ, tx, ty) using the closed-form:
     *
     *   [a  -b  tx]   [cos θ  -sin θ] * scale
     *   [b   a  ty]   [sin θ   cos θ]
     *
     * where  a = scale·cosθ,  b = scale·sinθ.
     *
     * Derivation follows Umeyama (1991), simplified for n=2 and isotropic scale.
     */
    private fun fitSimilarity(src: List<PointF>, dst: List<PointF>): Matrix {
        require(src.size == dst.size && src.isNotEmpty())
        val n = src.size.toFloat()

        val srcCx = src.sumOf { it.x.toDouble() }.toFloat() / n
        val srcCy = src.sumOf { it.y.toDouble() }.toFloat() / n
        val dstCx = dst.sumOf { it.x.toDouble() }.toFloat() / n
        val dstCy = dst.sumOf { it.y.toDouble() }.toFloat() / n

        var srcVar = 0f
        var a = 0f; var b = 0f
        for (i in src.indices) {
            val sx = src[i].x - srcCx; val sy = src[i].y - srcCy
            val dx = dst[i].x - dstCx; val dy = dst[i].y - dstCy
            srcVar += sx * sx + sy * sy
            a += sx * dx + sy * dy
            b += sx * dy - sy * dx
        }

        val scale = if (srcVar < 1e-6f) 1f else sqrt((a * a + b * b).toDouble()).toFloat() / srcVar
        val angle = atan2(b.toDouble(), a.toDouble()).toFloat()
        val cosA = scale * kotlin.math.cos(angle.toDouble()).toFloat()
        val sinA = scale * kotlin.math.sin(angle.toDouble()).toFloat()
        val tx = dstCx - cosA * srcCx + sinA * srcCy
        val ty = dstCy - sinA * srcCx - cosA * srcCy

        // Android Matrix is column-major [a, b, c, d, e, f] representing:
        // [a  b  tx]
        // [d  e  ty]
        // [g  h  i ]
        return Matrix().apply {
            setValues(
                floatArrayOf(
                    cosA, -sinA, tx,
                    sinA,  cosA, ty,
                    0f,    0f,   1f
                )
            )
        }
    }

    /** Maps a single point through the given matrix. */
    fun mapPoint(matrix: Matrix, pt: PointF): PointF {
        val arr = floatArrayOf(pt.x, pt.y)
        matrix.mapPoints(arr)
        return PointF(arr[0], arr[1])
    }

    /** Maps an array of (x0,y0,x1,y1,…) float coordinates through the matrix in place. */
    fun mapPoints(matrix: Matrix, pts: FloatArray): FloatArray {
        val out = pts.copyOf()
        matrix.mapPoints(out)
        return out
    }
}
