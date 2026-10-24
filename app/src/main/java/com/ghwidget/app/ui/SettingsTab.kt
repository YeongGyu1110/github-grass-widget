package com.ghwidget.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.reminder.Reminders
import com.ghwidget.app.ui.components.Alert
import com.ghwidget.app.ui.components.AppSwitch
import com.ghwidget.app.ui.components.AppButton
import com.ghwidget.app.ui.components.AppCard
import com.ghwidget.app.ui.components.AppDivider
import com.ghwidget.app.ui.components.AppIconButton
import com.ghwidget.app.ui.components.AppIcons
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
        PageHeader("설정")

        // 프로필
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(login, state.avatar, size = 48.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(login?.let { "@$it" } ?: "계정 확인 중…", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    StatusBadge("GitHub 연결됨", BadgeTone.Success)
                }
            }
        }

        ListGroup("화면", footer = "이 앱의 테마입니다. 홈 화면 위젯의 테마는 위젯 탭에서 따로 정합니다.") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("앱 테마", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                val themes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                SegmentedControl(
                    listOf("시스템", "라이트", "다크"),
                    themes.indexOf(state.settings.appTheme),
                    { actions.onSettingsChange(state.settings.copy(appTheme = themes[it])) },
                )
            }
        }

        ReminderGroup(state, actions)

        ListGroup("동기화", footer = "배터리가 부족하면 자동 새로고침을 건너뛰고, 그래프가 바뀌지 않으면 위젯을 다시 그리지 않습니다.") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("자동 새로고침 간격", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                SegmentedControl(
                    REFRESH_HOURS.map { "${it}시간" },
                    REFRESH_HOURS.indexOf(state.settings.refreshHours).coerceAtLeast(0),
                    { actions.onSettingsChange(state.settings.copy(refreshHours = REFRESH_HOURS[it])) },
                )
            }
            AppDivider()
            ListRow(
                "마지막 동기화",
                icon = AppIcons.Clock,
                value = when {
                    state.refreshing -> "동기화 중…"
                    state.data != null -> relativeTime(state.data.fetchedAt)
                    else -> "없음"
                },
            )
            AppDivider()
            ListRow(
                "지금 새로고침",
                icon = AppIcons.Refresh,
                onClick = actions.onRefresh.takeIf { !state.refreshing },
                trailing = if (state.refreshing) ({ Spinner(size = 14.dp) }) else null,
                showChevron = !state.refreshing,
            )
        }

        ListGroup("계정") {
            ListRow(
                "GitHub 권한 관리",
                icon = AppIcons.Key,
                description = "Authorized OAuth Apps에서 이 앱의 접근을 해제할 수 있어요",
                showChevron = true,
                onClick = { actions.onOpenUrl(GITHUB_APPS_URL) },
            )
            AppDivider()
            ListRow("로그아웃", icon = AppIcons.LogOut, destructive = true, onClick = { confirmSignOut = true })
        }

        ListGroup("정보") {
            ListRow("버전", icon = AppIcons.Info, value = state.versionName.ifEmpty { "-" })
            AppDivider()
            ListRow(
                "진단 기록",
                icon = AppIcons.Activity,
                description = "동기화·로그인·네트워크 기록 (문제 원인 확인용)",
                showChevron = true,
                onClick = { showDiagnostics = true },
            )
            AppDivider()
            ListRow(
                "소스 코드",
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
            title = { Text("로그아웃할까요?", style = MaterialTheme.typography.titleMedium, color = c.foreground) },
            text = {
                Text(
                    "이 기기에 저장된 토큰과 기여 데이터가 삭제되고, 위젯에는 로그인 안내가 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground,
                )
            },
            confirmButton = {
                AppButton("로그아웃", { confirmSignOut = false; actions.onSignOut() }, destructive = true)
            },
            dismissButton = {
                AppButton("취소", { confirmSignOut = false }, variant = ButtonVariant.Outline)
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
            AppIconButton(AppIcons.ChevronLeft, "뒤로", onBack)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("진단 기록", style = MaterialTheme.typography.headlineSmall, color = c.foreground)
                Text(
                    if (filtered) "${shown.size}건 표시 · 전체 ${entries.size}건 · 최신순" else "최근 ${entries.size}건 · 최신순",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                )
            }
            FilterChip(
                "오류만", ofKinds.count { it.isError }, selected = errorsOnly,
                destructive = true, icon = AppIcons.AlertCircle,
            ) { errorsOnly = !errorsOnly }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip("전체", ofSeverity.size, selected = kinds.isEmpty()) { kinds = emptySet() }
            DiagLog.Kind.entries.forEach { kind ->
                FilterChip(kind.label, ofSeverity.count { it.kind == kind }, selected = kind in kinds) {
                    kinds = if (kind in kinds) kinds - kind else kinds + kind
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                if (filtered) "보이는 기록 복사" else "복사",
                {
                    actions.onCopyDiagnostics(shown.reversed().joinToString("\n") { it.line })
                    Toast.makeText(context, "진단 기록 ${shown.size}건을 복사했습니다", Toast.LENGTH_SHORT).show()
                },
                variant = ButtonVariant.Outline,
                icon = AppIcons.Copy,
                enabled = shown.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
            AppButton(
                "모두 지우기",
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
                    if (entries.isEmpty()) "아직 기록이 없습니다" else "고른 종류의 기록이 없습니다",
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
            "${entry.time}  ·  ${entry.kind.label}",
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
        "잔디 알림",
        footer = "GitHub에서 오늘 기여가 0건일 때만 알려요. 커밋이 확인되면 그날 남은 알림은 보내지 않고, 알림의 '오늘은 그만'으로 멈출 수도 있어요.",
    ) {
        ListRow(
            "오늘 기여가 없으면 알림",
            icon = AppIcons.Bell,
            description = when {
                !s.reminderEnabled -> "늦은 시간까지 잔디가 비어 있으면 여러 번 나눠 알려 드려요"
                state.reminderNextAt > 0 -> "다음 확인: ${Reminders.describe(
                    java.time.Instant.ofEpochMilli(state.reminderNextAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                )}"
                else -> "켜짐"
            },
            trailing = { AppSwitch(s.reminderEnabled) { actions.onReminderToggle(it) } },
        )
        if (s.reminderEnabled) {
            if (!state.notificationsAllowed) {
                AppDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Alert("알림 권한이 꺼져 있어요", "권한을 켜야 잔디 알림을 받을 수 있어요.")
                    AppButton("알림 설정 열기", actions.onOpenNotificationSettings, variant = ButtonVariant.Outline, modifier = Modifier.fillMaxWidth())
                }
            }
            AppDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("첫 알림 시각", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                SegmentedControl(
                    REMINDER_HOURS.map { "${it}시" },
                    REMINDER_HOURS.indexOf(s.reminderStartHour).coerceAtLeast(0),
                    { actions.onSettingsChange(s.copy(reminderStartHour = REMINDER_HOURS[it])) },
                )
                Text("간격", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                SegmentedControl(
                    listOf("30분마다", "1시간마다"),
                    REMINDER_INTERVALS.indexOf(s.reminderIntervalMin).coerceAtLeast(0),
                    { actions.onSettingsChange(s.copy(reminderIntervalMin = REMINDER_INTERVALS[it])) },
                )
                val times = Reminders.slots(java.time.LocalDate.now(), s).map { it.toLocalTime().toString() }
                Text(
                    "${times.joinToString(" · ")} (하루 최대 ${times.size}번)",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                )
            }
            AppDivider()
            ListRow("알림 미리 보기", icon = AppIcons.Bell, showChevron = true, onClick = actions.onPreviewReminder)
        }
    }
}
