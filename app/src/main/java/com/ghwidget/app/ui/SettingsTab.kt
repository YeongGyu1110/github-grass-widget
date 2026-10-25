package com.ghwidget.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.R
import com.ghwidget.app.reminder.Reminders
import com.ghwidget.app.ui.components.Alert
import com.ghwidget.app.ui.components.AppButton
import com.ghwidget.app.ui.components.AppCard
import com.ghwidget.app.ui.components.AppDivider
import com.ghwidget.app.ui.components.AppIconButton
import com.ghwidget.app.ui.components.AppIcons
import com.ghwidget.app.ui.components.AppSwitch
import com.ghwidget.app.ui.components.BadgeTone
import com.ghwidget.app.ui.components.ButtonVariant
import com.ghwidget.app.ui.components.ListGroup
import com.ghwidget.app.ui.components.ListRow
import com.ghwidget.app.ui.components.PageHeader
import com.ghwidget.app.ui.components.SegmentedControl
import com.ghwidget.app.ui.components.Spinner
import com.ghwidget.app.ui.components.StatusBadge
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.ui.theme.Radius
import java.time.format.DateTimeFormatter
import java.time.LocalTime

private val REFRESH_HOURS = listOf(1, 3, 6, 12, 24)

@Composable
fun SettingsTab(state: HomeState, actions: HomeActions) {
    val c = AppTheme.colors
    val login = (state.account as? Account.SignedIn)?.login
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }

    if (showDiagnostics) {
        DiagnosticsPage(actions) { showDiagnostics = false }
        return
    }

    TabPage {
        PageHeader(stringResource(R.string.settings_title))

        // 프로필
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(login, state.avatar, size = 48.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(login?.let { "@$it" } ?: stringResource(R.string.checking_account), style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    StatusBadge(stringResource(R.string.connected), BadgeTone.Success)
                }
            }
        }

        ListGroup(stringResource(R.string.appearance_title), footer = stringResource(R.string.appearance_footer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.app_theme), style = MaterialTheme.typography.titleSmall, color = c.foreground)
                val themes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                SegmentedControl(
                    listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    themes.indexOf(state.settings.appTheme),
                    { actions.onSettingsChange(state.settings.copy(appTheme = themes[it])) },
                )
            }
        }

        if (state.languageSupported) {
            val languages = listOf("", "en", "ko")
            ListGroup(stringResource(R.string.language_title), footer = stringResource(R.string.language_footer)) {
                Column(Modifier.padding(16.dp)) {
                    SegmentedControl(
                        listOf(
                            stringResource(R.string.language_system),
                            stringResource(R.string.lang_english),
                            stringResource(R.string.lang_korean),
                        ),
                        languages.indexOf(state.appLanguage).coerceAtLeast(0),
                        { actions.onLanguageChange(languages[it]) },
                    )
                }
            }
        }

        ReminderGroup(state, actions)

        ListGroup(stringResource(R.string.sync_title), footer = stringResource(R.string.sync_footer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.refresh_interval), style = MaterialTheme.typography.titleSmall, color = c.foreground)
                SegmentedControl(
                    REFRESH_HOURS.map { stringResource(R.string.refresh_hours, it) },
                    REFRESH_HOURS.indexOf(state.settings.refreshHours).coerceAtLeast(0),
                    { actions.onSettingsChange(state.settings.copy(refreshHours = REFRESH_HOURS[it])) },
                )
            }
            AppDivider()
            ListRow(
                stringResource(R.string.last_synced),
                icon = AppIcons.Clock,
                value = when {
                    state.refreshing -> stringResource(R.string.syncing)
                    state.data != null -> relativeTime(LocalContext.current, state.data.fetchedAt)
                    else -> stringResource(R.string.never)
                },
            )
            AppDivider()
            ListRow(
                stringResource(R.string.refresh_now),
                icon = AppIcons.Refresh,
                onClick = actions.onRefresh.takeIf { !state.refreshing },
                trailing = if (state.refreshing) ({ Spinner(size = 14.dp) }) else null,
                showChevron = !state.refreshing,
            )
        }

        ListGroup(stringResource(R.string.account_title)) {
            ListRow(
                stringResource(R.string.manage_access),
                icon = AppIcons.Key,
                description = stringResource(R.string.manage_access_desc),
                showChevron = true,
                onClick = { actions.onOpenUrl(GITHUB_APPS_URL) },
            )
            AppDivider()
            ListRow(stringResource(R.string.sign_out), icon = AppIcons.LogOut, destructive = true, onClick = { confirmSignOut = true })
        }

        ListGroup(stringResource(R.string.about_title)) {
            ListRow(stringResource(R.string.version), icon = AppIcons.Info, value = state.versionName.ifEmpty { "-" })
            AppDivider()
            ListRow(
                stringResource(R.string.diagnostics),
                icon = AppIcons.Activity,
                description = stringResource(R.string.diagnostics_desc),
                showChevron = true,
                onClick = { showDiagnostics = true },
            )
            AppDivider()
            ListRow(
                stringResource(R.string.source_code),
                icon = AppIcons.Github,
                description = "YeongGyu1110/github-grass-widget",
                showChevron = true,
                onClick = { actions.onOpenUrl(REPO_URL) },
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = c.card,
            shape = Radius.card,
            title = { Text(stringResource(R.string.sign_out_confirm_title), style = MaterialTheme.typography.titleMedium, color = c.foreground) },
            text = {
                Text(
                    stringResource(R.string.sign_out_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground,
                )
            },
            confirmButton = {
                AppButton(stringResource(R.string.sign_out), { confirmSignOut = false; actions.onSignOut() }, destructive = true)
            },
            dismissButton = {
                AppButton(stringResource(R.string.cancel), { confirmSignOut = false }, variant = ButtonVariant.Outline)
            },
        )
    }
}

/**
 * 진단 기록: 동기화·로그인·토큰 갱신·잔디 알림 기록 (최신순).
 * 필터는 서로 독립인 두 가지:
 * - 종류 칩(여러 개 선택 가능): "전체"는 아무 종류도 고르지 않은 상태이고, 누르면 종류 선택을 지운다.
 *   네 종류를 다 골라도 "전체"로 바꾸지 않는다 (고른 상태에서 하나만 빼는 경우가 있으므로)
 * - 제목 옆 "오류만": 종류와 다른 기준이라 따로 둔다
 * 칩의 건수는 다른 쪽 필터를 반영해, 누르면 몇 건이 보일지 알려 준다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsPage(actions: HomeActions, onBack: () -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    var entries by remember { mutableStateOf(actions.loadDiagnostics()) }
    var kinds by remember { mutableStateOf(emptySet<DiagLog.Kind>()) }
    var errorsOnly by remember { mutableStateOf(false) }
    val ofSeverity = if (errorsOnly) entries.filter { it.isError } else entries
    val ofKinds = if (kinds.isEmpty()) entries else entries.filter { it.kind in kinds }
    val shown = ofKinds.filter { !errorsOnly || it.isError }
    val filtered = kinds.isNotEmpty() || errorsOnly
    BackHandler(onBack = onBack)

    TabPage {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            AppIconButton(AppIcons.ChevronLeft, stringResource(R.string.back), onBack)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.diagnostics), style = MaterialTheme.typography.headlineSmall, color = c.foreground)
                Text(
                    if (filtered) stringResource(R.string.diag_summary_filtered, shown.size, entries.size) else pluralResource(R.plurals.diag_summary, entries.size, entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                )
            }
            FilterChip(
                stringResource(R.string.diag_errors_only), ofKinds.count { it.isError }, selected = errorsOnly,
                destructive = true, icon = AppIcons.AlertCircle,
            ) { errorsOnly = !errorsOnly }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(stringResource(R.string.diag_all), ofSeverity.size, selected = kinds.isEmpty()) { kinds = emptySet() }
            DiagLog.Kind.entries.forEach { kind ->
                FilterChip(stringResource(kind.label), ofSeverity.count { it.kind == kind }, selected = kind in kinds) {
                    kinds = if (kind in kinds) kinds - kind else kinds + kind
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                if (filtered) stringResource(R.string.diag_copy_shown) else stringResource(R.string.diag_copy),
                {
                    actions.onCopyDiagnostics(shown.reversed().joinToString("\n") { it.line })
                    Toast.makeText(context, context.resources.getQuantityString(R.plurals.diag_copied, shown.size, shown.size), Toast.LENGTH_SHORT).show()
                },
                variant = ButtonVariant.Outline,
                icon = AppIcons.Copy,
                enabled = shown.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
            AppButton(
                stringResource(R.string.diag_clear_all),
                { actions.onClearDiagnostics(); entries = emptyList() },
                variant = ButtonVariant.Outline,
                destructive = true,
                enabled = entries.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
        AppCard {
            if (shown.isEmpty()) {
                Text(
                    if (entries.isEmpty()) stringResource(R.string.diag_empty) else stringResource(R.string.diag_empty_filtered),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shown.forEach { DiagnosticsEntry(it) }
                }
            }
        }
    }
}

/** 기록 한 건: 시각·종류 한 줄 + 내용 */
@Composable
private fun DiagnosticsEntry(entry: DiagLog.Entry) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            "${entry.time}  ·  ${stringResource(entry.kind.label)}",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = c.mutedForeground,
        )
        Text(
            entry.message,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = if (entry.isError) c.destructive else c.foreground,
        )
    }
}

/** 골라 보기 칩: 고르면 채워진다 */
@Composable
private fun FilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val fill = if (destructive) c.destructive else c.primary
    Row(
        Modifier
            .clip(Radius.pill)
            .then(if (selected) Modifier.background(fill) else Modifier.border(1.dp, c.border, Radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 다크 테마의 빨강은 밝아서 흰 글자 대신 primaryForeground(다크에서는 짙은 색)를 쓴다
        val fg = if (selected) c.primaryForeground else c.foreground
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = if (selected) fg else if (destructive) c.destructive else c.mutedForeground)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        Text("$count", style = MaterialTheme.typography.labelMedium, color = if (selected) fg.copy(alpha = 0.75f) else c.mutedForeground)
    }
}

private val REMINDER_HOURS = listOf(18, 20, 21, 22)
private val REMINDER_INTERVALS = listOf(30, 60)

/** 잔디 알림: 늦은 시간까지 오늘 기여가 없으면 여러 번 알림 */
@Composable
private fun ReminderGroup(state: HomeState, actions: HomeActions) {
    val c = AppTheme.colors
    val s = state.settings
    ListGroup(
        stringResource(R.string.reminder_title),
        footer = stringResource(R.string.reminder_footer),
    ) {
        ListRow(
            stringResource(R.string.reminder_toggle),
            icon = AppIcons.Bell,
            description = when {
                !s.reminderEnabled -> stringResource(R.string.reminder_toggle_off_desc)
                state.reminderNextAt > 0 -> stringResource(
                    R.string.reminder_next_check,
                    Reminders.describe(
                        LocalContext.current,
                        java.time.Instant.ofEpochMilli(state.reminderNextAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                    ),
                )
                else -> stringResource(R.string.reminder_on)
            },
            trailing = { AppSwitch(s.reminderEnabled) { actions.onReminderToggle(it) } },
        )
        if (s.reminderEnabled) {
            if (!state.notificationsAllowed) {
                AppDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Alert(stringResource(R.string.notif_off_title), stringResource(R.string.notif_off_body))
                    AppButton(stringResource(R.string.open_notification_settings), actions.onOpenNotificationSettings, variant = ButtonVariant.Outline, modifier = Modifier.fillMaxWidth())
                }
            }
            AppDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 시각 표시는 언어에 맞게 (한국어: 21시, 영어: 9 PM)
                val hourFormat = DateTimeFormatter.ofPattern(stringResource(R.string.reminder_hour_pattern))
                Text(stringResource(R.string.reminder_first_time), style = MaterialTheme.typography.titleSmall, color = c.foreground)
                SegmentedControl(
                    REMINDER_HOURS.map { LocalTime.of(it, 0).format(hourFormat) },
                    REMINDER_HOURS.indexOf(s.reminderStartHour).coerceAtLeast(0),
                    { actions.onSettingsChange(s.copy(reminderStartHour = REMINDER_HOURS[it])) },
                )
                Text(stringResource(R.string.reminder_interval), style = MaterialTheme.typography.titleSmall, color = c.foreground)
                SegmentedControl(
                    listOf(stringResource(R.string.reminder_every_30), stringResource(R.string.reminder_every_60)),
                    REMINDER_INTERVALS.indexOf(s.reminderIntervalMin).coerceAtLeast(0),
                    { actions.onSettingsChange(s.copy(reminderIntervalMin = REMINDER_INTERVALS[it])) },
                )
                val timeFormat = Reminders.timeFormat(LocalContext.current)
                val times = Reminders.slots(java.time.LocalDate.now(), s).map { it.format(timeFormat) }
                Text(
                    stringResource(R.string.reminder_times, times.joinToString(" · "), times.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                )
            }
            AppDivider()
            ListRow(stringResource(R.string.reminder_preview), icon = AppIcons.Bell, showChevron = true, onClick = actions.onPreviewReminder)
        }
    }
}
