package com.ghwidget.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * shadcn/ui 가 쓰는 Lucide 스타일(24px, 선 두께 2, 둥근 끝)의 선 아이콘.
 * Icon() 의 tint 로 색이 입혀진다.
 */
object AppIcons {
    val Refresh = icon(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    )
    val LogOut = icon("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9")
    val ExternalLink = icon(
        "M15 3h6v6",
        "M10 14L21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    )
    val Plus = icon("M5 12h14", "M12 5v14")
    val AlertCircle = icon(circle(12f, 12f, 10f), "M12 8v4", "M12 16h0.01")
    val Github = icon(
        "M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4",
        "M9 18c-4.51 2-5-2-7-2",
    )

    // 하단 탭
    val Dashboard = icon("M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3")
    val Widget = icon(rect(3f, 3f, 7f, 7f, 1f), rect(14f, 3f, 7f, 7f, 1f), rect(14f, 14f, 7f, 7f, 1f), rect(3f, 14f, 7f, 7f, 1f))
    val Settings = icon(
        "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3",
        "M14 2v4", "M8 10v4", "M16 18v4",
    )

    val Bell = icon("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")

    // 기타
    val ChevronRight = icon("M9 18l6-6-6-6")
    val ChevronLeft = icon("M15 18l-6-6 6-6")
    val Copy = icon(rect(8f, 8f, 14f, 14f, 2f), "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
    val Calendar = icon(rect(3f, 4f, 18f, 18f, 2f), "M16 2v4", "M8 2v4", "M3 10h18")
    val Flame = icon(
        "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z",
    )
    val Award = icon(circle(12f, 8f, 6f), "M15.477 12.89L17 22l-5-3-5 3 1.523-9.11")
    val Activity = icon("M22 12h-4l-3 9L9 3l-3 9H2")
    val Zap = icon("M13 2L3 14h9l-1 8 10-12h-9l1-8z")
    val Battery = icon(rect(2f, 7f, 16f, 10f, 2f), "M22 11v2")
    val Shield = icon("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", "M9 12l2 2 4-4")
    val Smartphone = icon(rect(5f, 2f, 14f, 20f, 2f), "M12 18h0.01")
    val Info = icon(circle(12f, 12f, 10f), "M12 16v-4", "M12 8h0.01")
    val Clock = icon(circle(12f, 12f, 10f), "M12 6v6l4 2")
    val Key = icon(
        circle(7.5f, 15.5f, 5.5f),
        "M21 2l-9.6 9.6",
        "M15.5 7.5l3 3L22 7l-3-3",
    )

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 -$r ${r}h-${w - 2 * r}a$r $r 0 0 1 -$r -${r}v-${h - 2 * r}a$r $r 0 0 1 $r -${r}z"

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 -${2 * r} 0z"

    private fun icon(vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = addPathNodes(d),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}
