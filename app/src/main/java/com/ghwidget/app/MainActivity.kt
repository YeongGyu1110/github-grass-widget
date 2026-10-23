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
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.data.TokenManager
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
        installSplashScreen().setOnExitAnimationListener { splash ->
            // 로고 애니메이션이 아직 진행 중이면 끝까지(최대 0.3초) 보여준 뒤 부드럽게 사라진다
            val remaining = (splash.iconAnimationStartMillis + splash.iconAnimationDurationMillis - SystemClock.uptimeMillis())
                .coerceIn(0L, 300L)
            // Android 12+ 에서는 로고가 배경과 다른 레이어에 그려져서 따로 함께 사라지게 해야 한다
            splash.iconView.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setStartDelay(remaining)
                .setDuration(200L)
                .start()
            splash.view.animate()
                .alpha(0f)
                .setStartDelay(remaining)
                .setDuration(220L)
                .withEndAction { splash.remove() }
                .start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DiagLog.log(this, if (savedInstanceState == null) "앱 화면 열림" else "앱 화면 다시 만들어짐 (테마 등 설정 변경)")
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
    var signOutNotice by remember { mutableStateOf(store.signOutNotice) }

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
            signOutNotice = store.signOutNotice
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
            if (new.appTheme != old.appTheme) DiagLog.log(ctx, "앱 테마 변경: ${old.appTheme} → ${new.appTheme}")
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
            DiagLog.log(ctx, "사용자가 직접 로그아웃")
            WorkManager.getInstance(ctx).cancelUniqueWork(Refresh.NOW)
            store.signOut()
            signOutNotice = null
            signedIn = false
            login = null
            data = null
            error = null
            avatar = null
            WidgetUpdater.updateAll(ctx)
        },
        onRefresh = { Refresh.now(ctx, "앱에서 새로고침") },
        onPinWidget = {
            widgetManager.requestPinAppWidget(ComponentName(ctx, ContributionWidgetProvider::class.java), null, null)
        },
        onOpenUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
        loadDiagnostics = { DiagLog.read(ctx) },
        onClearDiagnostics = { DiagLog.clear(ctx) },
        onCopyDiagnostics = { text ->
            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("GitHub 잔디 진단 기록", text))
        },
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
                    store.signIn(result.tokens)
                    DiagLog.log(
                        ctx,
                        "로그인 완료 (토큰 만료: ${result.tokens.accessExpiresAt?.let(TokenManager::time) ?: "없음"}, " +
                            "갱신 토큰: ${if (result.tokens.refreshToken != null) "있음" else "없음"})",
                    )
                    signOutNotice = null
                    pending = null
                    signedIn = true
                    login = null
                    data = null
                    error = null
                    loginError = null
                    WidgetUpdater.updateAll(ctx)
                    Refresh.ensurePeriodic(ctx)
                    Refresh.now(ctx, "로그인 직후")
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

    // 앱을 열거나 다시 돌아왔을 때: 데이터가 없거나 10분 넘게 지났거나 지난번 동기화가 실패했으면 다시 받는다.
    // (앱이 화면에 떠 있을 때는 네트워크가 막히지 않으므로, 백그라운드에서 실패한 동기화를 여기서 만회)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME || store.accessToken == null) return@LifecycleEventObserver
            val now = System.currentTimeMillis()
            val cached = store.cachedData()
            val stale = cached == null || now - cached.fetchedAt > 10 * 60_000L || store.lastError != null
            if (stale && now - store.lastAttemptAt > 60_000L) Refresh.now(ctx, "앱 열기 (데이터 오래됨/지난 실패)")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                // 동기화 중에는 지난번 오류를 숨긴다 (곧 결과가 나오므로)
                error = if (refreshing) null else error,
                signOutNotice = signOutNotice,
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
