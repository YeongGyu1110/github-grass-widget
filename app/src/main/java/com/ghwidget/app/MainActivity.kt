package com.ghwidget.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.ThemeMode
import com.ghwidget.app.widget.ContributionWidgetProvider
import com.ghwidget.app.widget.GraphRenderer
import com.ghwidget.app.widget.WidgetUpdater
import com.ghwidget.app.work.Refresh
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

    var settings by remember { mutableStateOf(store.settings()) }
    var username by remember { mutableStateOf(settings.username) }
    var token by remember { mutableStateOf(settings.token) }
    var data by remember { mutableStateOf(store.cachedData()) }
    var error by remember { mutableStateOf(store.lastError) }

    val workInfos by remember { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(Refresh.NOW) }
        .collectAsState(initial = emptyList())
    val refreshing = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    LaunchedEffect(refreshing) {
        if (!refreshing) {
            data = store.cachedData()
            error = store.lastError
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

    fun saveAndRefresh() {
        val userChanged = !username.trim().equals(settings.username, ignoreCase = true)
        if (userChanged) {
            data = null
            store.lastError = null
            error = null
        }
        apply(settings.copy(username = username.trim(), token = token.trim()))
        if (settings.username.isNotBlank()) Refresh.now(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("GitHub 잔디 위젯", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ---- 미리보기
        Preview(settings, data, error, refreshing)

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

        HorizontalDivider()

        // ---- 계정
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("GitHub 사용자 이름") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal access token (선택)") },
            supportingText = {
                Text("비워두면 공개 프로필 페이지에서 가져옵니다. 토큰을 쓰면 GraphQL API를 사용합니다 (fine-grained 토큰은 권한 없이도 가능, classic 토큰은 read:user).")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::saveAndRefresh, enabled = username.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("저장하고 새로고침")
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

    // 데이터가 없으면 앱을 열 때 받아온다
    LaunchedEffect(Unit) {
        if (settings.username.isNotBlank() && data == null && !refreshing) Refresh.now(ctx)
    }
}

@Composable
private fun Preview(settings: Settings, data: com.ghwidget.app.data.ContributionData?, error: String?, refreshing: Boolean) {
    val density = LocalDensity.current.density
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val message = when {
        settings.username.isBlank() -> "사용자 이름을 입력하세요"
        data == null -> if (refreshing) "불러오는 중…" else error ?: "불러오는 중…"
        else -> null
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
