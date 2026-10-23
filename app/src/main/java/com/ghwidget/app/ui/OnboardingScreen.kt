package com.ghwidget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghwidget.app.ui.components.Alert
import com.ghwidget.app.ui.components.AppButton
import com.ghwidget.app.ui.components.AppDivider
import com.ghwidget.app.ui.components.AppIcons
import com.ghwidget.app.ui.components.ButtonVariant
import com.ghwidget.app.ui.components.IconTile
import com.ghwidget.app.ui.components.Spinner
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.ui.theme.Radius

/** 로그인 전 전체 화면: 소개 + 아래 고정 로그인 영역 */
@Composable
fun OnboardingScreen(state: HomeState, actions: HomeActions) {
    val c = AppTheme.colors
    val sample = remember { sampleContributions() }
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LogoMark(size = 52.dp)
                Text(
                    "내 GitHub 잔디를\n홈 화면에서 바로",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp),
                    color = c.foreground,
                )
                Text(
                    "GitHub에 로그인하면 기여 그래프를 위젯으로 보여주고, 알아서 최신 상태로 유지합니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.mutedForeground,
                )
            }

            WidgetStage(state.settings, sample, null, state.systemDark)

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Feature(AppIcons.Smartphone, "크기에 맞춰 자동 조절", "위젯 크기를 바꾸면 보여주는 기간이 알아서 맞춰집니다.")
                Feature(AppIcons.Battery, "배터리 걱정 없는 동기화", "변경이 있을 때만 다시 그리고, 배터리가 부족하면 쉬어갑니다.")
                Feature(AppIcons.Shield, "읽기 전용 권한", "공개 정보 읽기 권한만 요청합니다. 저장소에는 접근하지 않습니다.")
            }
        }

        // 아래 고정 영역
        Column(Modifier.fillMaxWidth().background(c.card)) {
            AppDivider()
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.signOutNotice?.let { Alert("다시 로그인이 필요해요", it) }
                when (val account = state.account) {
                    is Account.Pending -> LoginCodePanel(account, actions)
                    else -> {
                        AppButton(
                            text = if (state.requestingCode) "준비 중…" else "GitHub로 계속하기",
                            onClick = actions.onLogin,
                            icon = AppIcons.Github,
                            loading = state.requestingCode,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        )
                        Text(
                            "브라우저에서 GitHub 승인 페이지가 열립니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.mutedForeground,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
                state.loginError?.let { Alert("로그인하지 못했습니다", it) }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, description: String) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon, size = 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
            Text(description, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
        }
    }
}

/** Device Flow 진행 중: GitHub 에 입력할 코드 */
@Composable
private fun LoginCodePanel(account: Account.Pending, actions: HomeActions) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GitHub에서 아래 코드를 입력하세요", style = MaterialTheme.typography.titleSmall, color = c.foreground)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(Radius.control)
                .background(c.muted)
                .border(1.dp, c.border, Radius.control)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            SelectionContainer {
                Text(
                    account.code.userCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    letterSpacing = 4.sp,
                    color = c.foreground,
                )
            }
        }
        AppButton(
            "코드 복사하고 GitHub 열기",
            actions.onOpenGitHub,
            icon = AppIcons.ExternalLink,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner(size = 12.dp)
            Spacer(Modifier.padding(start = 8.dp))
            Text(
                "승인하면 자동으로 로그인됩니다",
                style = MaterialTheme.typography.bodySmall,
                color = c.mutedForeground,
                modifier = Modifier.weight(1f),
            )
            AppButton("취소", actions.onCancelLogin, variant = ButtonVariant.Ghost)
        }
    }
}
