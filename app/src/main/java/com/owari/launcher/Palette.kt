package com.owari.launcher

import android.content.Context
import android.graphics.Color

/** Colores de la pantalla de inicio. Cada tema trae colores de texto que se leen bien sobre su fondo. */
data class Palette(
    val key: String,
    val name: String,
    val background: Int,
    val text: Int,
    val textSoft: Int,
    /** Círculos oscuros (TV, HDMI, ADMIN) y la pastilla del Wi-Fi. */
    val strong: Int,
    val onStrong: Int,
    /** Círculo del botón Wi-Fi. */
    val soft: Int,
    val alert: Int = Color.parseColor("#A32D2D"),
) {
    companion object {
        private fun c(hex: String) = Color.parseColor(hex)

        val ALL = listOf(
            Palette("amarillo", "Amarillo", c("#F6C90E"), c("#2B2100"), c("#5C4700"), c("#2B2100"), c("#F6C90E"), c("#FFF3B0")),
            Palette("azul", "Azul noche", c("#14213D"), c("#FFFFFF"), c("#9FB3D9"), c("#FCA311"), c("#14213D"), c("#2A3D66")),
            Palette("negro", "Negro", c("#0E0E0E"), c("#FFFFFF"), c("#A0A0A0"), c("#F6C90E"), c("#0E0E0E"), c("#2A2A2A")),
            Palette("verde", "Verde", c("#0F6E56"), c("#FFFFFF"), c("#C8EBDD"), c("#FAC775"), c("#04342C"), c("#1D9E75")),
            Palette("rosado", "Rosado", c("#F4C0D1"), c("#4B1528"), c("#72243E"), c("#4B1528"), c("#F4C0D1"), c("#FBEAF0")),
            Palette("morado", "Morado", c("#3C3489"), c("#FFFFFF"), c("#CECBF6"), c("#EF9F27"), c("#26215C"), c("#534AB7")),
        )

        private const val PREFS = "ajustes"
        private const val KEY = "color_fondo"

        fun current(context: Context): Palette {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return ALL.firstOrNull { it.key == saved } ?: ALL.first()
        }

        fun save(context: Context, palette: Palette) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, palette.key).apply()
        }
    }
}
