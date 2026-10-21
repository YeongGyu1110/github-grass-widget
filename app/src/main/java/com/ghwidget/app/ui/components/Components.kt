package com.ghwidget.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.ui.theme.Radius

// ------------------------------------------------------------------ 카드

/** 흰 바탕 + 1px 테두리 카드 (그림자 없음) */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(c.card)
            .border(1.dp, c.border, Radius.card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun CardHeader(
    title: String,
    description: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.foreground)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.mutedForeground)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun AppDivider() = HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.foreground, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.mutedForeground)
        }
    }
}

// ------------------------------------------------------------------ 버튼

enum class ButtonVariant { Primary, Outline, Ghost }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
) {
    val c = AppTheme.colors
    val padding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            Spinner(size = 14.dp, color = spinnerColorFor(variant, destructive))
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    val m = modifier.height(40.dp)
    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick = onClick, modifier = m, enabled = enabled && !loading, shape = Radius.control,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (destructive) c.destructive else c.primary,
                contentColor = if (destructive) Color.White else c.primaryForeground,
                disabledContainerColor = (if (destructive) c.destructive else c.primary).copy(alpha = 0.5f),
                disabledContentColor = (if (destructive) Color.White else c.primaryForeground).copy(alpha = 0.8f),
            ),
            elevation = null,
            content = content,
        )
        ButtonVariant.Outline -> OutlinedButton(
            onClick = onClick, modifier = m, enabled = enabled && !loading, shape = Radius.control,
            contentPadding = padding,
            border = BorderStroke(1.dp, c.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = c.card,
                contentColor = if (destructive) c.destructive else c.foreground,
                disabledContentColor = c.mutedForeground,
            ),
            content = content,
        )
        ButtonVariant.Ghost -> TextButton(
            onClick = onClick, modifier = m, enabled = enabled && !loading, shape = Radius.control,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (destructive) c.destructive else c.mutedForeground,
            ),
            content = content,
        )
    }
}

@Composable
private fun spinnerColorFor(variant: ButtonVariant, destructive: Boolean): Color {
    val c = AppTheme.colors
    return when (variant) {
        ButtonVariant.Primary -> if (destructive) Color.White else c.primaryForeground
        else -> if (destructive) c.destructive else c.foreground
    }
}

/** 테두리 있는 정사각형 아이콘 버튼 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(Radius.control)
            .background(c.card)
            .border(1.dp, c.border, Radius.control)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) Spinner(size = 14.dp, color = c.mutedForeground)
        else Icon(icon, contentDescription, tint = c.foreground, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun Spinner(size: Dp = 16.dp, color: Color = AppTheme.colors.mutedForeground) {
    CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = 2.dp)
}

// ------------------------------------------------------------------ 입력

/** shadcn Tabs 처럼 생긴 단일 선택 컨트롤 */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.control)
            .background(c.muted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .then(
                        if (selected) Modifier
                            .shadow(1.dp, RoundedShape6, clip = false)
                            .clip(RoundedShape6)
                            .background(if (c.isDark) c.border else c.card)
                        else Modifier.clip(RoundedShape6)
                    )
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) c.foreground else c.mutedForeground,
                    maxLines = 1,
                )
            }
        }
    }
}

private val RoundedShape6 = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.primaryForeground,
            checkedTrackColor = c.primary,
            checkedBorderColor = c.primary,
            uncheckedThumbColor = if (c.isDark) c.mutedForeground else Color.White,
            uncheckedTrackColor = c.border,
            uncheckedBorderColor = c.border,
        ),
    )
}

/** 얇은 트랙 + 테두리 있는 동그란 손잡이 (shadcn Slider 모양) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, steps: Int, range: ClosedFloatingPointRange<Float>) {
    val c = AppTheme.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range,
        steps = steps,
        thumb = {
            Box(
                Modifier
                    .size(20.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.card)
                    .border(2.dp, c.primary, CircleShape)
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill).background(c.muted)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(Radius.pill).background(c.primary))
            }
        },
    )
}

/** 제목 + 설명 + 오른쪽 컨트롤 한 줄 */
@Composable
fun SettingRow(title: String, description: String? = null, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.foreground)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.mutedForeground)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

// ------------------------------------------------------------------ 표시

enum class BadgeTone { Neutral, Success, Destructive }

@Composable
fun StatusBadge(text: String, tone: BadgeTone = BadgeTone.Neutral) {
    val c = AppTheme.colors
    val dot = when (tone) {
        BadgeTone.Neutral -> c.mutedForeground
        BadgeTone.Success -> c.success
        BadgeTone.Destructive -> c.destructive
    }
    Row(
        Modifier
            .clip(Radius.pill)
            .border(1.dp, c.border, Radius.pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.foreground)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    val c = AppTheme.colors
    Column(
        modifier
            .clip(Radius.control)
            .border(1.dp, c.border, Radius.control)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.mutedForeground, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = c.foreground, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** 오류 안내 상자 */
@Composable
fun Alert(title: String, message: String) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.control)
            .background(c.destructiveMuted)
            .border(1.dp, c.destructive.copy(alpha = 0.35f), Radius.control)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.AlertCircle, null, tint = c.destructive, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.destructive)
            Text(message, style = MaterialTheme.typography.bodySmall, color = c.destructive)
        }
    }
}

/** 점 목록 한 줄 */
@Composable
fun Bullet(text: String) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 7.dp).size(4.dp).clip(CircleShape).background(c.mutedForeground))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
    }
}

@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
        color = AppTheme.colors.mutedForeground,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

// ------------------------------------------------------------------ 페이지 구조

/** 탭 화면 상단 제목 + 오른쪽 동작 버튼 */
@Composable
fun PageHeader(title: String, subtitle: String? = null, actions: (@Composable RowScope.() -> Unit)? = null) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.foreground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
        }
        if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** 하단 탭 막대 항목 */
data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun BottomNavBar(items: List<NavItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(c.card)) {
        AppDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .clip(Radius.control)
                        .clickable { onSelect(i) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .clip(Radius.pill)
                            .background(if (selected) c.muted else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            item.icon, null,
                            tint = if (selected) c.foreground else c.mutedForeground,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) c.foreground else c.mutedForeground,
                    )
                }
            }
        }
    }
}

/** 설정 목록 묶음: 작은 제목 + 테두리 카드 안에 행들 */
@Composable
fun ListGroup(title: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = c.mutedForeground, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.card)
                .background(c.card)
                .border(1.dp, c.border, Radius.card),
            content = content,
        )
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** 목록 한 행: 아이콘 + 제목/설명 + 오른쪽 값 또는 컨트롤 */
@Composable
fun ListRow(
    title: String,
    icon: ImageVector? = null,
    description: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (destructive) c.destructive else c.mutedForeground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (destructive) c.destructive else c.foreground)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = c.mutedForeground)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.ChevronRight, null, tint = c.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}

/** 둥근 사각 배경 안의 아이콘 (기능 소개·통계 카드에 사용) */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 32.dp, tint: Color = AppTheme.colors.foreground) {
    val c = AppTheme.colors
    Box(
        Modifier.size(size).clip(Radius.control).background(c.muted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
