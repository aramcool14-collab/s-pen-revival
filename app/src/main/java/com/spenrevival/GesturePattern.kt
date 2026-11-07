package com.spenrevival

import kotlin.math.abs
import kotlin.math.sqrt

data class GesturePoint(val x: Float, val y: Float)

object GesturePattern {
    private const val SAMPLE_COUNT = 24

    fun encode(points: List<GesturePoint>): String =
        resample(normalize(points), SAMPLE_COUNT).joinToString(";") { "${it.x},${it.y}" }

    fun decode(value: String?): List<GesturePoint> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(";").mapNotNull { pair ->
            val bits = pair.split(",")
            if (bits.size != 2) null else try {
                GesturePoint(bits[0].toFloat(), bits[1].toFloat())
            } catch (_: NumberFormatException) { null }
        }
    }

    fun matches(input: List<GesturePoint>, template: List<GesturePoint>, tolerance: Float = 0.22f): Boolean {
        if (input.size < 5 || template.size < 5) return false
        val a = resample(normalize(input), SAMPLE_COUNT)
        val b = resample(normalize(template), SAMPLE_COUNT)
        val direct = averageDistance(a, b)
        val reverse = averageDistance(a, b.reversed())
        return minOf(direct, reverse) <= tolerance
    }

    private fun normalize(points: List<GesturePoint>): List<GesturePoint> {
        if (points.isEmpty()) return emptyList()
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val size = maxOf(maxX - minX, maxY - minY).coerceAtLeast(1f)
        return points.map {
            GesturePoint((it.x - minX) / size, (it.y - minY) / size)
        }
    }

    private fun resample(points: List<GesturePoint>, count: Int): List<GesturePoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(count) { points[0] }
        val lengths = mutableListOf<Float>()
        var total = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            total += sqrt(dx * dx + dy * dy)
            lengths.add(total)
        }
        if (total <= 0f) return List(count) { points.first() }
        return (0 until count).map { sample ->
            val target = total * sample / (count - 1).coerceAtLeast(1)
            val index = lengths.indexOfFirst { it >= target }.let { if (it < 0) lengths.lastIndex else it }
            val before = if (index == 0) 0f else lengths[index - 1]
            val segment = (lengths[index] - before).coerceAtLeast(0.0001f)
            val t = ((target - before) / segment).coerceIn(0f, 1f)
            val p0 = points[index]
            val p1 = points[index + 1]
            GesturePoint(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
        }
    }

    private fun averageDistance(a: List<GesturePoint>, b: List<GesturePoint>): Float {
        if (a.size != b.size || a.isEmpty()) return Float.MAX_VALUE
        return a.indices.map { i ->
            val dx = a[i].x - b[i].x
            val dy = a[i].y - b[i].y
            sqrt(dx * dx + dy * dy)
        }.average().toFloat()
    }
}
