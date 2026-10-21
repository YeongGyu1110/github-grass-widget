package com.ghwidget.app

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.ui.Account
import com.ghwidget.app.ui.HomeActions
import com.ghwidget.app.ui.MainScreen
import com.ghwidget.app.ui.HomeState
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.widget.ContributionWidgetProvider
import com.ghwidget.app.widget.WidgetUpdater
import com.ghwidget.app.work.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MainRoute(this) }
    }
}

/** 저장소·WorkManager·로그인 상태를 들고 MainScreen 에 넘겨준다 */
@Composable
private fun MainRoute(activity: ComponentActivity) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()
    val widgetManager = remember { AppWidgetManager.getInstance(ctx) }
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()?.let { "v$it" } ?: ""
    }

    var settings by remember { mutableStateOf(store.settings()) }
    var data by remember { mutableStateOf(store.cachedData()) }
    var error by remember { mutableStateOf(store.lastError) }
    var signedIn by remember { mutableStateOf(store.accessToken != null) }
    var login by remember { mutableStateOf(store.login) }
    var pending by remember { mutableStateOf(store.pendingLogin) }
    var requestingCode by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var avatar by remember { mutableStateOf(loadAvatar(store)) }

    val workInfos by remember { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(Refresh.NOW) }
        .collectAsState(initial = emptyList())
    val refreshing = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    LaunchedEffect(refreshing) {
        if (!refreshing) {
            // 작업이 끝나면 저장된 상태를 다시 읽는다 (토큰 만료로 로그아웃됐을 수도 있음)
            data = store.cachedData()
            error = store.lastError
            login = store.login
            signedIn = store.accessToken != null
            avatar = loadAvatar(store)
        }
    }

    fun cancelLogin() {
        store.pendingLogin = null
        pending = null
    }

    val actions = HomeActions(
        onSettingsChange = { new: Settings ->
            val old = settings
            settings = new
            store.save(new)
            // 앱 테마·새로고침 간격만 바뀐 경우에는 위젯을 다시 그리지 않는다
            if (new.copy(appTheme = old.appTheme, refreshHours = old.refreshHours) != old) WidgetUpdater.updateAll(ctx)
            if (new.refreshHours != old.refreshHours) Refresh.ensurePeriodic(ctx)
        },
        onLogin = {
            scope.launch {
                requestingCode = true
                loginError = null
                try {
                    val code = withContext(Dispatchers.IO) { GitHubAuth.requestDeviceCode() }
                    store.pendingLogin = code
                    pending = code
                } catch (e: Exception) {
                    loginError = e.message ?: e.javaClass.simpleName
                } finally {
                    requestingCode = false
                }
            }
        },
        onOpenGitHub = { pending?.let { openGitHubWithCode(ctx, it) } },
        onCancelLogin = ::cancelLogin,
        onSignOut = {
            WorkManager.getInstance(ctx).cancelUniqueWork(Refresh.NOW)
            store.signOut()
            signedIn = false
            login = null
            data = null
            error = null
            avatar = null
            WidgetUpdater.updateAll(ctx)
        },
        onRefresh = { Refresh.now(ctx) },
        onPinWidget = {
            widgetManager.requestPinAppWidget(ComponentName(ctx, ContributionWidgetProvider::class.java), null, null)
        },
        onOpenUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
    )

    // 진행 중인 로그인이 있으면 승인될 때까지 interval 초마다 확인한다
    LaunchedEffect(pending) {
        val code = pending ?: return@LaunchedEffect
        var interval = code.interval
        while (true) {
            if (System.currentTimeMillis() > code.expiresAt) {
                loginError = "로그인 코드가 만료되었습니다. 다시 로그인해 주세요."
                cancelLogin()
                break
            }
            delay(interval * 1000L)
            // 일시적인 네트워크 오류는 무시하고 다음 주기에 다시 확인
            val result = runCatching {
                withContext(Dispatchers.IO) { GitHubAuth.poll(code.deviceCode, interval) }
            }.getOrDefault(GitHubAuth.Poll.Pending)
            when (result) {
                GitHubAuth.Poll.Pending -> {}
                is GitHubAuth.Poll.SlowDown -> interval = result.interval
                is GitHubAuth.Poll.Success -> {
                    store.signIn(result.token)
                    pending = null
                    signedIn = true
                    login = null
                    data = null
                    error = null
                    loginError = null
                    WidgetUpdater.updateAll(ctx)
                    Refresh.ensurePeriodic(ctx)
                    Refresh.now(ctx)
                    break
                }
                is GitHubAuth.Poll.Failed -> {
                    loginError = result.message
                    cancelLogin()
                    break
                }
            }
        }
    }

    // 로그인돼 있는데 데이터가 없으면 앱을 열 때 받아온다
    LaunchedEffect(Unit) {
        if (signedIn && data == null && !refreshing) Refresh.now(ctx)
    }

    // 앱 테마: 설정이 '시스템'이면 휴대폰 설정을 따른다
    val systemDark = isSystemInDarkTheme()
    val appDark = when (settings.appTheme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // 상태 표시줄·내비게이션 바 아이콘 색도 앱 테마에 맞춘다
    LaunchedEffect(appDark) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (appDark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val currentPending = pending
    AppTheme(darkTheme = appDark) {
        MainScreen(
            state = HomeState(
                settings = settings,
                account = when {
                    signedIn -> Account.SignedIn(login)
                    currentPending != null -> Account.Pending(currentPending)
                    else -> Account.SignedOut
                },
                data = data,
                error = error,
                refreshing = refreshing,
                requestingCode = requestingCode,
                loginError = loginError,
                canPinWidget = widgetManager.isRequestPinAppWidgetSupported,
                versionName = versionName,
                systemDark = systemDark,
                avatar = avatar,
            ),
            actions = actions,
        )
    }
}

private fun loadAvatar(store: Store): ImageBitmap? =
    store.avatarFile.takeIf { it.exists() }
        ?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }

private fun openGitHubWithCode(ctx: Context, code: GitHubAuth.DeviceCode) {
    ctx.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("GitHub 로그인 코드", code.userCode))
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))) }
}
