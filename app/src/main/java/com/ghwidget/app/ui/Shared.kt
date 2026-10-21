package com.ghwidget.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.ui.theme.Radius
import com.ghwidget.app.widget.GraphRenderer
import com.ghwidget.app.widget.Palette
import java.time.LocalDate
import kotlin.random.Random

/** 2x2 잔디 칸 로고 */
@Composable
fun LogoMark(size: Dp = 40.dp) {
    val c = AppTheme.colors
    val levels = Palette.of(PaletteId.GREEN, dark = true).levels
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(if (c.isDark) c.muted else c.primary),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.5f)) {
            val cell = this.size.width * 0.42f
            val gap = this.size.width - cell * 2
            listOf(levels[4], levels[2], levels[1], levels[3]).forEachIndexed { i, col ->
                drawRoundRect(
                    color = Color(col),
                    topLeft = Offset((i % 2) * (cell + gap), (i / 2) * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.25f),
                )
            }
        }
    }
}

/** 계정 이름 첫 글자 원형 아바타 */
@Composable
fun Avatar(login: String?, size: Dp = 40.dp) {
    val c = AppTheme.colors
    Box(
        Modifier.size(size).clip(CircleShape).background(c.muted).border(1.dp, c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            login?.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.4f).sp),
            color = c.foreground,
        )
    }
}

/** 미리보기용 위젯 크기 (런처 칸 수 기준 대략적인 dp) */
enum class PreviewSize(val label: String, val widthDp: Float, val heightDp: Float) {
    S2x1("2×1", 170f, 80f),
    S4x1("4×1", 340f, 80f),
    S4x2("4×2", 340f, 160f),
    S5x3("5×3", 420f, 240f),
}

/**
 * 홈 화면 배경 위에 위젯을 올려둔 듯한 미리보기.
 * 위젯은 실제 위젯과 같은 GraphRenderer 로 그린다.
 */
@Composable
fun WidgetStage(
    settings: Settings,
    data: ContributionData?,
    message: String?,
    systemDark: Boolean,
    size: PreviewSize = PreviewSize.S4x2,
) {
    val c = AppTheme.colors
    val density = LocalDensity.current.density
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val bmp = remember(settings, data, message, dark, density, size) {
        GraphRenderer.render(size.widthDp, size.heightDp, density, dark, settings, data, message).asImageBitmap()
    }
    val wallpaper = if (c.isDark) listOf(Color(0xFF1C1C21), Color(0xFF2A2A31)) else listOf(Color(0xFFE6E6EA), Color(0xFFF6F6F7))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.control)
            .background(Brush.linearGradient(wallpaper))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            bitmap = bmp,
            contentDescription = "위젯 미리보기",
            modifier = Modifier
                .fillMaxWidth(size.widthDp / PreviewSize.S5x3.widthDp)
                .aspectRatio(size.widthDp / size.heightDp),
        )
        // 홈 화면처럼 보이도록 앱 아이콘 자리 표시
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(4) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (c.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                )
            }
        }
    }
}

fun relativeTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    // 1분 안쪽은 "0분 전" 대신 "방금 전"
    if (now - epochMs in 0 until DateUtils.MINUTE_IN_MILLIS) return "방금 전"
    return DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** 로그인 전 소개 화면에 쓰는 보기용 데이터 (항상 같은 모양) */
fun sampleContributions(login: String = "you"): ContributionData {
    val rnd = Random(11)
    val today = LocalDate.now()
    val days = (0L until 371L).map { i ->
        val date = today.minusDays(370 - i)
        val weekday = date.dayOfWeek.value % 7
        val busy = if (weekday in 1..5) 3 else 1
        val level = (rnd.nextInt(busy + 4) - 2).coerceIn(0, 4)
        ContributionDay(date, if (level == 0) 0 else level * 2 + rnd.nextInt(3), level)
    }
    return ContributionData(login, days, days.sumOf { it.count }, System.currentTimeMillis())
}
