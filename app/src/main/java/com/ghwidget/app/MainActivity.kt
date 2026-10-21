package com.ghwidget.app

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.widget.ContributionWidgetProvider
import com.ghwidget.app.widget.GraphRenderer
import com.ghwidget.app.widget.WidgetUpdater
import com.ghwidget.app.work.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppTheme { SettingsScreen() } }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun SettingsScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(store.settings()) }
    var data by remember { mutableStateOf(store.cachedData()) }
    var error by remember { mutableStateOf(store.lastError) }

    // 로그인 상태
    var signedIn by remember { mutableStateOf(store.accessToken != null) }
    var login by remember { mutableStateOf(store.login) }
    var pending by remember { mutableStateOf(store.pendingLogin) }
    var requestingCode by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

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
        }
    }

    /** 모양 관련 설정은 즉시 저장하고 위젯에 반영 */
    fun apply(new: Settings) {
        val intervalChanged = new.refreshHours != settings.refreshHours
        settings = new
        store.save(new)
        WidgetUpdater.updateAll(ctx)
        if (intervalChanged) Refresh.ensurePeriodic(ctx)
    }

    fun startLogin() = scope.launch {
        requestingCode = true
        loginError = null
        try {
            val code = withContext(Dispatchers.IO) { GitHubAuth.requestDeviceCode() }
            store.pendingLogin = code
            pending = code
        } catch (e: Exception) {
            loginError = "로그인을 시작하지 못했습니다: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            requestingCode = false
        }
    }

    fun cancelLogin() {
        store.pendingLogin = null
        pending = null
    }

    fun signOut() {
        WorkManager.getInstance(ctx).cancelUniqueWork(Refresh.NOW)
        store.signOut()
        signedIn = false
        login = null
        data = null
        error = null
        WidgetUpdater.updateAll(ctx)
    }

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

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("GitHub 잔디 위젯", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ---- 미리보기
        val previewMessage = when {
            !signedIn -> "GitHub에 로그인하세요"
            data == null -> if (refreshing) "불러오는 중…" else error ?: "불러오는 중…"
            else -> null
        }
        Preview(settings, data, previewMessage)

        if (signedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        refreshing -> "새로고침 중…"
                        data != null -> "마지막 업데이트: " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(data!!.fetchedAt))
                        else -> "아직 데이터가 없습니다"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            if (error != null) {
                Text("오류: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        HorizontalDivider()

        // ---- 계정
        Text("GitHub 계정", style = MaterialTheme.typography.titleSmall)
        val currentPending = pending
        when {
            signedIn -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = login?.let { "@$it 으로 로그인됨" } ?: "로그인됨 · 계정 확인 중…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = ::signOut) { Text("로그아웃") }
                }
                Text(
                    "로그아웃하면 이 기기에서 토큰이 삭제됩니다. GitHub 쪽 권한까지 없애려면 GitHub → Settings → Applications → Authorized OAuth Apps 에서 이 앱을 Revoke 하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            currentPending != null -> LoginCodeCard(
                code = currentPending,
                onOpenGitHub = { openGitHubWithCode(ctx, currentPending) },
                onCancel = ::cancelLogin,
            )
            else -> {
                Button(
                    onClick = { startLogin() },
                    enabled = !requestingCode,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (requestingCode) "로그인 준비 중…" else "GitHub로 로그인") }
                Text(
                    "로그인한 계정 본인의 잔디를 보여줍니다. 공개 정보 읽기 권한만 요청합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        loginError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider()

        // ---- 모양
        Section("테마") {
            ThemeMode.entries.forEach { m ->
                FilterChip(
                    selected = settings.theme == m,
                    onClick = { apply(settings.copy(theme = m)) },
                    label = { Text(when (m) { ThemeMode.SYSTEM -> "시스템"; ThemeMode.LIGHT -> "라이트"; ThemeMode.DARK -> "다크" }) },
                )
            }
        }
        Section("색상") {
            PaletteId.entries.forEach { p ->
                FilterChip(
                    selected = settings.palette == p,
                    onClick = { apply(settings.copy(palette = p)) },
                    label = { Text(p.label) },
                )
            }
        }

        Column {
            Text("배경 불투명도 ${settings.bgOpacity}%", style = MaterialTheme.typography.titleSmall)
            var opacity by remember { mutableStateOf(settings.bgOpacity.toFloat()) }
            Slider(
                value = opacity,
                onValueChange = { opacity = it; settings = settings.copy(bgOpacity = it.toInt()) },
                onValueChangeFinished = { apply(settings.copy(bgOpacity = opacity.toInt())) },
                valueRange = 0f..100f,
                steps = 19,
            )
        }

        SwitchRow("상단 헤더 (이름 · 총 기여 · 연속일)", settings.showHeader) { apply(settings.copy(showHeader = it)) }
        SwitchRow("월 표시", settings.showMonths) { apply(settings.copy(showMonths = it)) }

        Section("자동 새로고침 간격") {
            listOf(1, 3, 6, 12, 24).forEach { h ->
                FilterChip(
                    selected = settings.refreshHours == h,
                    onClick = { apply(settings.copy(refreshHours = h)) },
                    label = { Text("${h}시간") },
                )
            }
        }

        HorizontalDivider()

        val manager = remember { AppWidgetManager.getInstance(ctx) }
        if (manager.isRequestPinAppWidgetSupported) {
            OutlinedButton(
                onClick = {
                    manager.requestPinAppWidget(ComponentName(ctx, ContributionWidgetProvider::class.java), null, null)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("홈 화면에 위젯 추가") }
        }
        Text(
            "위젯 사용법: 그래프를 누르면 새로고침, 상단 이름 부분을 누르면 이 설정 화면이 열립니다. 위젯을 길게 눌러 크기를 바꾸면 표시되는 주(week) 수가 자동으로 맞춰집니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }

    // 로그인돼 있는데 데이터가 없으면 앱을 열 때 받아온다
    LaunchedEffect(Unit) {
        if (signedIn && data == null && !refreshing) Refresh.now(ctx)
    }
}

/** Device Flow 진행 중: 사용자가 GitHub 에 입력할 코드를 보여준다 */
@Composable
private fun LoginCodeCard(code: GitHubAuth.DeviceCode, onOpenGitHub: () -> Unit, onCancel: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("GitHub에서 아래 코드를 입력하고 승인하세요", style = MaterialTheme.typography.bodyMedium)
            SelectionContainer {
                Text(
                    code.userCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 2.sp,
                )
            }
            Button(onClick = onOpenGitHub, modifier = Modifier.fillMaxWidth()) {
                Text("코드 복사하고 GitHub 열기")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    "승인을 기다리는 중… (승인하면 자동으로 로그인됩니다)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) { Text("취소") }
        }
    }
}

private fun openGitHubWithCode(ctx: Context, code: GitHubAuth.DeviceCode) {
    ctx.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("GitHub 로그인 코드", code.userCode))
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))) }
}

@Composable
private fun Preview(settings: Settings, data: ContributionData?, message: String?) {
    val density = LocalDensity.current.density
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val bmp = remember(settings, data, message, dark, density) {
        GraphRenderer.render(340f, 150f, density, dark, settings, data, message).asImageBitmap()
    }
    Image(
        bitmap = bmp,
        contentDescription = "위젯 미리보기",
        modifier = Modifier.fillMaxWidth().aspectRatio(340f / 150f),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
