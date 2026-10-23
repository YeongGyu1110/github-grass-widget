package com.ghwidget.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghwidget.app.data.ThemeMode
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

/** 진단 기록: 동기화·로그인·토큰 갱신·네트워크 오류 기록 (최신순) */
@Composable
private fun DiagnosticsPage(actions: HomeActions, onBack: () -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    var lines by remember { mutableStateOf(actions.loadDiagnostics()) }
    BackHandler(onBack = onBack)

    TabPage {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            AppIconButton(AppIcons.ChevronLeft, "뒤로", onBack)
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("진단 기록", style = MaterialTheme.typography.headlineSmall, color = c.foreground)
                Text("최근 ${lines.size}건 · 최신순", style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                "복사",
                {
                    actions.onCopyDiagnostics(lines.reversed().joinToString("\n"))
                    Toast.makeText(context, "진단 기록을 복사했습니다", Toast.LENGTH_SHORT).show()
                },
                variant = ButtonVariant.Outline,
                icon = AppIcons.Copy,
                enabled = lines.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
            AppButton(
                "지우기",
                { actions.onClearDiagnostics(); lines = emptyList() },
                variant = ButtonVariant.Outline,
                destructive = true,
                enabled = lines.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
        AppCard {
            if (lines.isEmpty()) {
                Text("아직 기록이 없습니다", style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEach { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = if (line.contains("실패") || line.contains("로그아웃") || line.contains("401")) c.destructive else c.foreground,
                        )
                    }
                }
            }
        }
    }
}
