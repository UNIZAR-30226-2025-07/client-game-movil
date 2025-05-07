package eina.unizar.cliente_movil.utils

import kotlin.math.sqrt

object VectorMath {
    fun normalize(x: Float, y: Float): Pair<Float, Float> {
        val length = sqrt(x * x + y * y)
        return if (length > 0) {
            Pair(x / length, y / length)
        } else {
            Pair(0f, 0f)
        }
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}