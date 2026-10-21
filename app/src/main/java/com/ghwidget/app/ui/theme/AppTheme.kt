package com.ghwidget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * shadcn/ui 스타일의 색 토큰.
 * 순수 회색(zinc) 대신 푸른 회색(slate)을 아주 살짝 섞어 덜 딱딱하게 하고,
 * 눈부심을 줄이려고 완전한 흰색·검정은 쓰지 않는다 (라이트: 미색 바탕, 다크: 아주 옅게 푸른 검정).
 * 색은 상태 표시(성공·오류)와 잔디 초록에만 쓴다.
 */
@Immutable
data class AppColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val border: Color,
    val muted: Color,
    val mutedForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val accent: Color,
    val destructive: Color,
    val destructiveMuted: Color,
    val success: Color,
    val isDark: Boolean,
)

private val Light = AppColors(
    background = Color(0xFFF2F4F7),       // 살짝 푸른 미색
    foreground = Color(0xFF1C232C),       // 완전한 검정 대신 짙은 슬레이트
    card = Color(0xFFFAFBFC),             // 순백 대신 아주 옅은 회백색
    border = Color(0xFFDDE2E8),
    muted = Color(0xFFEAEEF2),
    mutedForeground = Color(0xFF5F6A77),
    primary = Color(0xFF232B35),
    primaryForeground = Color(0xFFF7F9FB),
    accent = Color(0xFFEAEEF2),
    destructive = Color(0xFFCF3A3A),
    destructiveMuted = Color(0xFFFBEDED),
    success = Color(0xFF1F9D55),
    isDark = false,
)

// 다크: zinc 원래 값에서 슬레이트 차콜 쪽으로 15%만 옮긴 색 (너무 밝아지지 않게)
private val Dark = AppColors(
    background = Color(0xFF0B0B0E),
    foreground = Color(0xFFF6F7F8),
    card = Color(0xFF121316),
    border = Color(0xFF28292D),
    muted = Color(0xFF1F2025),
    mutedForeground = Color(0xFF9EA0A9),
    primary = Color(0xFFF6F7F8),
    primaryForeground = Color(0xFF17181B),
    accent = Color(0xFF28292D),
    destructive = Color(0xFFF77272),
    destructiveMuted = Color(0xFF2A1417),
    success = Color(0xFF4DDB80),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { Light }

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

object Radius {
    val card = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(8.dp)
    val pill = RoundedCornerShape(percent = 50)
}

private val typography = Typography().let { t ->
    t.copy(
        headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) Dark else Light
    // Material 컴포넌트(Slider, Switch, 진행 표시 등)도 같은 회색 톤을 쓰도록 맞춘다. 동적 색상(Material You)은 쓰지 않음
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.primaryForeground,
            secondary = c.mutedForeground, onSecondary = c.foreground,
            background = c.background, onBackground = c.foreground,
            surface = c.card, onSurface = c.foreground,
            surfaceVariant = c.muted, onSurfaceVariant = c.mutedForeground,
            surfaceContainerHighest = c.border,
            outline = c.border, outlineVariant = c.border,
            error = c.destructive,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.primaryForeground,
            secondary = c.mutedForeground, onSecondary = c.foreground,
            background = c.background, onBackground = c.foreground,
            surface = c.card, onSurface = c.foreground,
            surfaceVariant = c.muted, onSurfaceVariant = c.mutedForeground,
            surfaceContainerHighest = c.border,
            outline = c.border, outlineVariant = c.border,
            error = c.destructive,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(small = Radius.control, medium = Radius.control, large = Radius.card),
            content = content,
        )
    }
}
