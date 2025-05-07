package eina.unizar.cliente_movil.utils

import android.graphics.Color
import kotlin.random.Random
import eina.unizar.cliente_movil.utils.Constants

object ColorUtils {
    fun getRandomColor(): String {
        val index = Random.nextInt(Constants.PLAYER_COLORS.size)
        return Constants.PLAYER_COLORS[index]
    }

    fun parseColor(colorString: String): Int {
        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            Color.parseColor("#FF4136")  // Color por defecto
        }
    }

    fun getContrastColor(colorString: String): Int {
        val color = parseColor(colorString)
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}