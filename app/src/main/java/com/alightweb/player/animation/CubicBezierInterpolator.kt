package com.alightweb.player.animation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance pure Kotlin Cubic Bézier Curve Solver for Keyframe Animations
 */
class CubicBezierInterpolator(
    private val p1x: Float,
    private val p1y: Float,
    private val p2x: Float,
    private val p2y: Float
) {

    private val cx = 3.0f * p1x
    private val bx = 3.0f * (p2x - p1x) - cx
    private val ax = 1.0f - cx - bx

    private val cy = 3.0f * p1y
    private val by = 3.0f * (p2y - p1y) - cy
    private val ay = 1.0f - cy - by

    fun getInterpolation(time: Float): Float {
        val t = max(0f, min(1f, time))
        if (p1x == p1y && p2x == p2y) return t // linear

        val solvedT = solveX(t)
        return sampleY(solvedT)
    }

    private fun sampleX(t: Float): Float = ((ax * t + bx) * t + cx) * t
    private fun sampleY(t: Float): Float = ((ay * t + by) * t + cy) * t
    private fun sampleXDerivative(t: Float): Float = (3.0f * ax * t + 2.0f * bx) * t + cx

    private fun solveX(x: Float): Float {
        var t = x
        // Newton-Raphson iteration
        for (i in 0 until 5) {
            val currentX = sampleX(t) - x
            if (abs(currentX) < 1e-5f) return t
            val dX = sampleXDerivative(t)
            if (abs(dX) < 1e-5f) break
            t -= currentX / dX
        }

        // Bisection fallback
        var t0 = 0.0f
        var t1 = 1.0f
        t = x

        while (t0 < t1) {
            val currentX = sampleX(t)
            if (abs(currentX - x) < 1e-4f) return t
            if (x > currentX) t0 = t else t1 = t
            t = (t1 - t0) * 0.5f + t0
        }
        return t
    }

    companion object {
        fun parse(curveStr: String?): CubicBezierInterpolator {
            if (curveStr.isNullOrBlank() || curveStr == "linear") {
                return CubicBezierInterpolator(0f, 0f, 1f, 1f)
            }
            try {
                if (curveStr.startsWith("cubicBezier")) {
                    val parts = curveStr.removePrefix("cubicBezier").trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        return CubicBezierInterpolator(
                            parts[0].toFloat(),
                            parts[1].toFloat(),
                            parts[2].toFloat(),
                            parts[3].toFloat()
                        )
                    }
                } else if (curveStr.contains(",")) {
                    val parts = curveStr.split(",").map { it.trim().toFloat() }
                    if (parts.size >= 4) {
                        return CubicBezierInterpolator(parts[0], parts[1], parts[2], parts[3])
                    }
                }
            } catch (e: Exception) {
                // Ignore and fallback to linear
            }
            return CubicBezierInterpolator(0.25f, 0.1f, 0.25f, 1.0f)
        }
    }
}
