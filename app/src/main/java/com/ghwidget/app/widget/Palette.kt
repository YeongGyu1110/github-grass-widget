package com.ghwidget.app.widget

import android.graphics.Color
import com.ghwidget.app.data.PaletteId

class Palette(
    val background: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    /** index 0 = 기여 없음, 1~4 = 단계 */
    val levels: IntArray,
) {
    companion object {
        private fun c(hex: String) = Color.parseColor(hex)

        fun of(id: PaletteId, dark: Boolean): Palette {
            val levels = if (dark) when (id) {
                PaletteId.GREEN -> listOf("#0E4429", "#006D32", "#26A641", "#39D353")
                PaletteId.BLUE -> listOf("#0C2D6B", "#1158C7", "#388BFD", "#79C0FF")
                PaletteId.PURPLE -> listOf("#3C1E70", "#6E40C9", "#A371F7", "#D2A8FF")
                PaletteId.ORANGE -> listOf("#5A1E02", "#9B4215", "#DB6D28", "#FFA657")
            } else when (id) {
                PaletteId.GREEN -> listOf("#9BE9A8", "#40C463", "#30A14E", "#216E39")
                PaletteId.BLUE -> listOf("#C0DDF9", "#73B3F3", "#3886E1", "#1B4F91")
                PaletteId.PURPLE -> listOf("#E2CBFA", "#C297F0", "#9A5AE0", "#6431A8")
                PaletteId.ORANGE -> listOf("#FFDF9E", "#FFB347", "#F0812A", "#B8501A")
            }
            return if (dark) Palette(
                background = c("#0D1117"),
                textPrimary = c("#E6EDF3"),
                textSecondary = c("#9198A1"),
                levels = intArrayOf(c("#161B22")) + levels.map(::c),
            ) else Palette(
                background = c("#FFFFFF"),
                textPrimary = c("#1F2328"),
                textSecondary = c("#59636E"),
                levels = intArrayOf(c("#EBEDF0")) + levels.map(::c),
            )
        }
    }
}
