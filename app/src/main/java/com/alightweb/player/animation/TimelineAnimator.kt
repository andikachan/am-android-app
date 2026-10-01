package com.alightweb.player.animation

import com.alightweb.player.model.Keyframe
import com.alightweb.player.model.PropertyTimeline
import com.alightweb.player.model.Vec2
import com.alightweb.player.model.Vec3

object TimelineAnimator {

    fun evaluateFloat(timeline: PropertyTimeline<Float>, timeMs: Long, durationMs: Long): Float {
        if (!timeline.isAnimated()) return timeline.defaultValue
        val kfs = timeline.keyframes.sortedBy { if (it.time > 0) it.time else (it.normalizedTime * durationMs).toLong() }
        if (kfs.isEmpty()) return timeline.defaultValue

        val tFirst = if (kfs.first().time > 0) kfs.first().time else (kfs.first().normalizedTime * durationMs).toLong()
        val tLast = if (kfs.last().time > 0) kfs.last().time else (kfs.last().normalizedTime * durationMs).toLong()

        if (timeMs <= tFirst) return kfs.first().value
        if (timeMs >= tLast) return kfs.last().value

        for (i in 0 until kfs.size - 1) {
            val k0 = kfs[i]
            val k1 = kfs[i + 1]
            val t0 = if (k0.time > 0) k0.time else (k0.normalizedTime * durationMs).toLong()
            val t1 = if (k1.time > 0) k1.time else (k1.normalizedTime * durationMs).toLong()

            if (timeMs in t0..t1) {
                val span = (t1 - t0).toFloat()
                if (span <= 0f) return k0.value
                val rawProgress = (timeMs - t0).toFloat() / span
                val easeProgress = CubicBezierInterpolator.parse(k0.curve).getInterpolation(rawProgress)
                return k0.value + (k1.value - k0.value) * easeProgress
            }
        }
        return kfs.last().value
    }

    fun evaluateVec2(timeline: PropertyTimeline<Vec2>, timeMs: Long, durationMs: Long): Vec2 {
        if (!timeline.isAnimated()) return timeline.defaultValue
        val kfs = timeline.keyframes.sortedBy { if (it.time > 0) it.time else (it.normalizedTime * durationMs).toLong() }
        if (kfs.isEmpty()) return timeline.defaultValue

        val tFirst = if (kfs.first().time > 0) kfs.first().time else (kfs.first().normalizedTime * durationMs).toLong()
        val tLast = if (kfs.last().time > 0) kfs.last().time else (kfs.last().normalizedTime * durationMs).toLong()

        if (timeMs <= tFirst) return kfs.first().value
        if (timeMs >= tLast) return kfs.last().value

        for (i in 0 until kfs.size - 1) {
            val k0 = kfs[i]
            val k1 = kfs[i + 1]
            val t0 = if (k0.time > 0) k0.time else (k0.normalizedTime * durationMs).toLong()
            val t1 = if (k1.time > 0) k1.time else (k1.normalizedTime * durationMs).toLong()

            if (timeMs in t0..t1) {
                val span = (t1 - t0).toFloat()
                if (span <= 0f) return k0.value
                val rawProgress = (timeMs - t0).toFloat() / span
                val easeProgress = CubicBezierInterpolator.parse(k0.curve).getInterpolation(rawProgress)
                val x = k0.value.x + (k1.value.x - k0.value.x) * easeProgress
                val y = k0.value.y + (k1.value.y - k0.value.y) * easeProgress
                return Vec2(x, y)
            }
        }
        return kfs.last().value
    }

    fun evaluateVec3(timeline: PropertyTimeline<Vec3>, timeMs: Long, durationMs: Long): Vec3 {
        if (!timeline.isAnimated()) return timeline.defaultValue
        val kfs = timeline.keyframes.sortedBy { if (it.time > 0) it.time else (it.normalizedTime * durationMs).toLong() }
        if (kfs.isEmpty()) return timeline.defaultValue

        val tFirst = if (kfs.first().time > 0) kfs.first().time else (kfs.first().normalizedTime * durationMs).toLong()
        val tLast = if (kfs.last().time > 0) kfs.last().time else (kfs.last().normalizedTime * durationMs).toLong()

        if (timeMs <= tFirst) return kfs.first().value
        if (timeMs >= tLast) return kfs.last().value

        for (i in 0 until kfs.size - 1) {
            val k0 = kfs[i]
            val k1 = kfs[i + 1]
            val t0 = if (k0.time > 0) k0.time else (k0.normalizedTime * durationMs).toLong()
            val t1 = if (k1.time > 0) k1.time else (k1.normalizedTime * durationMs).toLong()

            if (timeMs in t0..t1) {
                val span = (t1 - t0).toFloat()
                if (span <= 0f) return k0.value
                val rawProgress = (timeMs - t0).toFloat() / span
                val easeProgress = CubicBezierInterpolator.parse(k0.curve).getInterpolation(rawProgress)
                val x = k0.value.x + (k1.value.x - k0.value.x) * easeProgress
                val y = k0.value.y + (k1.value.y - k0.value.y) * easeProgress
                val z = k0.value.z + (k1.value.z - k0.value.z) * easeProgress
                return Vec3(x, y, z)
            }
        }
        return kfs.last().value
    }
}
