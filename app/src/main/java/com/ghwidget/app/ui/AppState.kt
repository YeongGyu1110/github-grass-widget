package com.ghwidget.app.ui

import com.ghwidget.app.data.ContributionData
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
)

const val REPO_URL = "https://github.com/YeongGyu1110/github-grass-widget"
const val GITHUB_APPS_URL = "https://github.com/settings/applications"
