package com.ghwidget.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.Settings

sealed interface Account {
    data object SignedOut : Account
    data class Pending(val code: GitHubAuth.DeviceCode) : Account
    /** login 은 첫 데이터를 받기 전까지 null */
    data class SignedIn(val login: String?) : Account
}

/** 로그인 후 하단 탭 */
enum class Tab { Dashboard, Widget, Settings }

data class HomeState(
    val settings: Settings,
    val account: Account,
    val data: ContributionData?,
    val error: String?,
    val refreshing: Boolean,
    val requestingCode: Boolean = false,
    val loginError: String? = null,
    val canPinWidget: Boolean = true,
    val versionName: String = "",
    /** 미리보기에서 '시스템' 테마를 어느 쪽으로 그릴지 */
    val systemDark: Boolean = false,
    /** GitHub 프로필 이미지 (받기 전이면 null → 이니셜 표시) */
    val avatar: ImageBitmap? = null,
    /** 자동으로 로그아웃된 이유 (로그인 화면에 보여줌) */
    val signOutNotice: String? = null,
    /** 다음 잔디 알림 확인 시각 (epoch ms), 0 이면 없음 */
    val reminderNextAt: Long = 0L,
    /** 알림 권한이 있는지 */
    val notificationsAllowed: Boolean = true,
    /** 이 앱만 쓰는 언어: "" 이면 휴대폰 언어를 따름, "en", "ko" */
    val appLanguage: String = "",
    /** 앱 안에서 언어를 바꿀 수 있는지 (Android 13+ 앱별 언어) */
    val languageSupported: Boolean = false,
)

class HomeActions(
    val onSettingsChange: (Settings) -> Unit = {},
    val onLogin: () -> Unit = {},
    val onOpenGitHub: () -> Unit = {},
    val onCancelLogin: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onPinWidget: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    /** 진단 기록 (최근 것이 먼저) */
    val loadDiagnostics: () -> List<DiagLog.Entry> = { emptyList() },
    val onClearDiagnostics: () -> Unit = {},
    val onCopyDiagnostics: (String) -> Unit = {},
    /** 잔디 알림 켜기/끄기 (켤 때 알림 권한 요청) */
    val onReminderToggle: (Boolean) -> Unit = {},
    val onPreviewReminder: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    /** "" = 휴대폰 언어 따르기 */
    val onLanguageChange: (String) -> Unit = {},
)

const val REPO_URL = "https://github.com/YeongGyu1110/github-grass-widget"
const val GITHUB_APPS_URL = "https://github.com/settings/applications"
