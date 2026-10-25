package com.ghwidget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghwidget.app.R
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
                    stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp),
                    color = c.foreground,
                )
                Text(
                    stringResource(R.string.onboarding_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.mutedForeground,
                )
            }

            WidgetStage(state.settings, sample, null, state.systemDark)

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Feature(AppIcons.Smartphone, stringResource(R.string.feature_resize_title), stringResource(R.string.feature_resize_desc))
                Feature(AppIcons.Battery, stringResource(R.string.feature_battery_title), stringResource(R.string.feature_battery_desc))
                Feature(AppIcons.Shield, stringResource(R.string.feature_readonly_title), stringResource(R.string.feature_readonly_desc))
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
                state.signOutNotice?.let { Alert(stringResource(R.string.sign_in_again_title), it) }
                when (val account = state.account) {
                    is Account.Pending -> LoginCodePanel(account, actions)
                    else -> {
                        AppButton(
                            text = if (state.requestingCode) stringResource(R.string.preparing) else stringResource(R.string.continue_with_github),
                            onClick = actions.onLogin,
                            icon = AppIcons.Github,
                            loading = state.requestingCode,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        )
                        Text(
                            stringResource(R.string.opens_browser),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.mutedForeground,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
                state.loginError?.let { Alert(stringResource(R.string.sign_in_failed), it) }
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
        Text(stringResource(R.string.enter_code), style = MaterialTheme.typography.titleSmall, color = c.foreground)
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
            stringResource(R.string.copy_code_open),
            actions.onOpenGitHub,
            icon = AppIcons.ExternalLink,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner(size = 12.dp)
            Spacer(Modifier.padding(start = 8.dp))
            Text(
                stringResource(R.string.signed_in_after_authorize),
                style = MaterialTheme.typography.bodySmall,
                color = c.mutedForeground,
                modifier = Modifier.weight(1f),
            )
            AppButton(stringResource(R.string.cancel), actions.onCancelLogin, variant = ButtonVariant.Ghost)
        }
    }
}
