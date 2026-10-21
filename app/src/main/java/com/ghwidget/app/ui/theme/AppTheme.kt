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
 * shadcn/ui 의 "zinc" 테마를 옮긴 색 토큰.
 * 회색 계열 + 1px 테두리 중심이고, 색은 상태 표시(성공·오류)와 잔디 초록에만 쓴다.
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
    background = Color(0xFFFAFAFA),       // zinc-50
    foreground = Color(0xFF09090B),       // zinc-950
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE4E4E7),           // zinc-200
    muted = Color(0xFFF4F4F5),            // zinc-100
    mutedForeground = Color(0xFF71717A),  // zinc-500
    primary = Color(0xFF18181B),          // zinc-900
    primaryForeground = Color(0xFFFAFAFA),
    accent = Color(0xFFF4F4F5),
    destructive = Color(0xFFDC2626),      // red-600
    destructiveMuted = Color(0xFFFEF2F2), // red-50
    success = Color(0xFF16A34A),          // green-600
    isDark = false,
)

private val Dark = AppColors(
    background = Color(0xFF09090B),       // zinc-950
    foreground = Color(0xFFFAFAFA),
    card = Color(0xFF111113),
    border = Color(0xFF27272A),           // zinc-800
    muted = Color(0xFF1F1F23),
    mutedForeground = Color(0xFFA1A1AA),  // zinc-400
    primary = Color(0xFFFAFAFA),
    primaryForeground = Color(0xFF18181B),
    accent = Color(0xFF27272A),
    destructive = Color(0xFFF87171),      // red-400
    destructiveMuted = Color(0xFF2A1215),
    success = Color(0xFF4ADE80),          // green-400
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
