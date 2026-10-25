package com.ghwidget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.R
import com.ghwidget.app.ui.components.AppButton
import com.ghwidget.app.ui.components.AppCard
import com.ghwidget.app.ui.components.AppDivider
import com.ghwidget.app.ui.components.AppIcons
import com.ghwidget.app.ui.components.AppSlider
import com.ghwidget.app.ui.components.AppSwitch
import com.ghwidget.app.ui.components.Bullet
import com.ghwidget.app.ui.components.CardHeader
import com.ghwidget.app.ui.components.FieldLabel
import com.ghwidget.app.ui.components.IconTile
import com.ghwidget.app.ui.components.ListGroup
import com.ghwidget.app.ui.components.ListRow
import com.ghwidget.app.ui.components.PageHeader
import com.ghwidget.app.ui.components.SegmentedControl
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.ui.theme.Radius
import com.ghwidget.app.widget.Palette

@Composable
fun WidgetTab(state: HomeState, actions: HomeActions) {
    val settings = state.settings
    val update = actions.onSettingsChange
    var previewSize by rememberSaveable { mutableStateOf(PreviewSize.S4x2) }

    TabPage {
        PageHeader(stringResource(R.string.widget_title), stringResource(R.string.widget_subtitle))

        // 미리보기 + 크기 전환
        AppCard {
            CardHeader(stringResource(R.string.preview_title), stringResource(R.string.preview_subtitle))
            WidgetStage(
                settings,
                state.data,
                if (state.data == null) (state.error ?: stringResource(R.string.loading)) else null,
                state.systemDark,
                previewSize,
            )
            SegmentedControl(
                PreviewSize.entries.map { it.label },
                previewSize.ordinal,
                { previewSize = PreviewSize.entries[it] },
            )
        }

        // 테마·색상·불투명도
        AppCard {
            CardHeader(stringResource(R.string.style_title), null)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(R.string.field_theme))
                val themes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                SegmentedControl(
                    listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    themes.indexOf(settings.theme),
                    { update(settings.copy(theme = themes[it])) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(R.string.field_color))
                PalettePicker(settings.palette) { update(settings.copy(palette = it)) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // 드래그 중에는 화면에서만 바꾸고, 손을 떼면 저장·위젯 반영
                var opacity by remember(settings.bgOpacity) { mutableFloatStateOf(settings.bgOpacity.toFloat()) }
                FieldLabel(stringResource(R.string.field_bg_opacity), trailing = "${opacity.toInt()}%")
                AppSlider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    onValueChangeFinished = { update(settings.copy(bgOpacity = opacity.toInt())) },
                    steps = 19,
                    range = 0f..100f,
                )
            }
        }

        // 표시 요소
        DisplayGroup(settings, update)

        // 홈 화면에 추가
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(AppIcons.Smartphone, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.home_widget_title), style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.foreground)
                    Text(stringResource(R.string.home_widget_subtitle), style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedForeground)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bullet(stringResource(R.string.widget_tip_refresh))
                Bullet(stringResource(R.string.widget_tip_open))
                Bullet(stringResource(R.string.widget_tip_resize))
            }
            if (state.canPinWidget) {
                AppButton(stringResource(R.string.add_widget), actions.onPinWidget, icon = AppIcons.Plus, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DisplayGroup(settings: Settings, update: (Settings) -> Unit) {
    ListGroup(stringResource(R.string.display_title)) {
        ListRow(stringResource(R.string.display_header), description = stringResource(R.string.display_header_desc), trailing = {
            AppSwitch(settings.showHeader) { update(settings.copy(showHeader = it)) }
        })
        AppDivider()
        ListRow(stringResource(R.string.display_months), description = stringResource(R.string.display_months_desc), trailing = {
            AppSwitch(settings.showMonths) { update(settings.copy(showMonths = it)) }
        })
    }
}

/** 각 색상의 4단계를 작은 칸으로 보여주는 선택지 */
@Composable
private fun PalettePicker(selected: PaletteId, onSelect: (PaletteId) -> Unit) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteId.entries.forEach { p ->
            val isSelected = p == selected
            val levels = Palette.of(p, dark = c.isDark).levels
            Column(
                Modifier
                    .weight(1f)
                    .clip(Radius.control)
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.primary else c.border, Radius.control)
                    .clickable { onSelect(p) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (lv in 1..4) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Color(levels[lv])))
                    }
                }
                Text(stringResource(p.label), style = MaterialTheme.typography.labelMedium, color = if (isSelected) c.foreground else c.mutedForeground)
            }
        }
    }
}
