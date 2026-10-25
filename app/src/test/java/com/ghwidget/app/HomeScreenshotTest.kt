package com.ghwidget.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.drawToBitmap
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.widget.Palette
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.ui.Account
import com.ghwidget.app.ui.charts.ContributionHeatmap
import com.ghwidget.app.ui.HomeActions
import com.ghwidget.app.ui.MainScreen
import com.ghwidget.app.ui.Tab
import com.ghwidget.app.ui.HomeState
import com.ghwidget.app.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * 앱 화면을 상태별·테마별로 그려 app/build/screenshots 에 PNG 로 저장한다 (눈으로 확인용).
 * 화면 전체가 한 장에 담기도록 세로로 긴 창에서 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w400dp-h1700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun sampleData(): ContributionData {
        val rnd = Random(7)
        val today = LocalDate.now()
        val days = (0L until 371L).map { i ->
            val level = (rnd.nextInt(9) - 4).coerceIn(0, 4)
            val count = if (i >= 362) 2 else if (level == 0) 0 else level * 3 + rnd.nextInt(3)
            ContributionDay(today.minusDays(370 - i), count, if (i >= 362) maxOf(level, 1) else level)
        }
        return ContributionData("octocat", days, days.sumOf { it.count }, System.currentTimeMillis() - 4 * 60_000)
    }

    private fun shoot(name: String, dark: Boolean, state: HomeState, tab: Tab = Tab.Dashboard) {
        compose.setContent {
            AppTheme(darkTheme = dark) { MainScreen(state.copy(systemDark = dark), HomeActions(), initialTab = tab) }
        }
        capture(name)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // captureToImage 는 Robolectric 에서 다시 그리기를 기다리다 멈추므로 뷰를 직접 비트맵에 그린다
        val bmp = compose.activity.window.decorView.drawToBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val base = HomeState(
        settings = Settings(),
        account = Account.SignedOut,
        data = null,
        error = null,
        refreshing = false,
        versionName = "v1.8.0",
        languageSupported = true,
    )

    @Test fun signedInLight() = shoot("signed_in_light", false,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()))

    @Test fun signedInDark() = shoot("signed_in_dark", true,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()))

    @Test fun signedOutLight() = shoot("signed_out_light", false, base)

    @Test fun pendingDark() = shoot("pending_dark", true,
        base.copy(account = Account.Pending(GitHubAuth.DeviceCode("dc", "6AEC-C38C", "https://github.com/login/device", 5, Long.MAX_VALUE))))

    @Test fun errorLight() = shoot("error_light", false,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData(), error = "GitHub 서버 오류 (HTTP 502)"))

    @Test fun widgetTabLight() = shoot("widget_light", false,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()), Tab.Widget)

    @Test fun widgetTabDark() = shoot("widget_dark", true,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()), Tab.Widget)

    @Test fun settingsTabLight() = shoot("settings_light", false,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()), Tab.Settings)

    @Test fun settingsTabDark() = shoot("settings_dark", true,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()), Tab.Settings)

    @Test fun settingsReminderLight() = shoot("settings_reminder_light", false,
        base.copy(
            account = Account.SignedIn("octocat"), data = sampleData(),
            settings = Settings(reminderEnabled = true, reminderStartHour = 21, reminderIntervalMin = 30),
            reminderNextAt = System.currentTimeMillis() + 3_600_000L,
        ),
        Tab.Settings,
    )

    // 영어 기기
    @Test @Config(qualifiers = "en-w400dp-h1700dp-xhdpi")
    fun signedInLightEnglish() = shoot("en_signed_in_light", false,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()))

    @Test @Config(qualifiers = "en-w400dp-h1700dp-xhdpi")
    fun signedOutEnglish() = shoot("en_signed_out_light", false, base)

    @Test @Config(qualifiers = "en-w400dp-h1700dp-xhdpi")
    fun pendingEnglish() = shoot("en_pending_dark", true,
        base.copy(account = Account.Pending(GitHubAuth.DeviceCode("dc", "6AEC-C38C", "https://github.com/login/device", 5, Long.MAX_VALUE))))

    @Test @Config(qualifiers = "en-w400dp-h1700dp-xhdpi")
    fun widgetTabEnglish() = shoot("en_widget_light", false,
        base.copy(account = Account.SignedIn("octocat"), data = sampleData()), Tab.Widget)

    @Test @Config(qualifiers = "en-w400dp-h1700dp-xhdpi")
    fun settingsReminderEnglish() = shoot("en_settings_reminder_light", false,
        base.copy(
            account = Account.SignedIn("octocat"), data = sampleData(),
            settings = Settings(reminderEnabled = true, reminderStartHour = 21, reminderIntervalMin = 30),
            reminderNextAt = System.currentTimeMillis() + 3_600_000L,
        ),
        Tab.Settings,
    )

    @Test fun loadingLight() = shoot("loading_light", false, base.copy(account = Account.SignedIn("octocat"), refreshing = true))

    /** 이번 주(맨 오른쪽 열) 칸을 고른 그래프: 선택 테두리 오른쪽이 잘리지 않아야 한다 */
    @Test fun heatmapSelectedThisWeek() {
        val data = sampleData()
        compose.setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.width(400.dp).padding(16.dp)) {
                    ContributionHeatmap(data, Palette.of(PaletteId.GREEN, dark = false).levels, data.lastDate) {}
                }
            }
        }
        capture("heatmap_selected_this_week")
    }

    private val sampleLog = listOf(
        "10-24 14:33:37  동기화 성공 (변화 없음)",
        "10-24 14:33:36  동기화 시작: 앱 열기 (데이터 오래됨/지난 실패) (시도 1, 네트워크: Wi-Fi)",
        "10-24 14:33:36  앱 화면 열림",
        "10-24 12:10:05  동기화 실패: 인터넷에 연결할 수 없어 GitHub 주소를 찾지 못했습니다 [UnknownHostException: api.github.com] → 잠시 뒤 재시도",
        "10-24 10:48:38  토큰 갱신 성공 → 다음 만료 10-24 18:48",
        "10-24 10:48:38  토큰 갱신 시도 (만료됨: 10-24 09:01)",
        "10-24 10:48:38  동기화 시작: 주기 갱신 (시도 1, 네트워크: Wi-Fi)",
        "10-23 22:07:00  잔디 알림 예약: 내일 21:00 (오늘은 건너뜀)",
        "10-23 22:07:00  잔디 알림 켬",
    ).map(DiagLog::parse)

    /** 설정 → 진단 기록으로 들어가 chips 를 차례로 누른 화면 */
    private fun openDiagnostics(name: String, vararg chips: String) {
        compose.setContent {
            AppTheme(darkTheme = false) {
                MainScreen(
                    base.copy(account = Account.SignedIn("octocat"), data = sampleData()),
                    HomeActions(loadDiagnostics = { sampleLog }),
                    initialTab = Tab.Settings,
                )
            }
        }
        compose.onNodeWithText("진단 기록").performScrollTo().performClick()
        chips.forEach { compose.onNodeWithText(it).performClick() }
        capture(name)
    }

    @Test fun diagnosticsAll() = openDiagnostics("diagnostics_all")

    @Test fun diagnosticsFiltered() = openDiagnostics("diagnostics_filtered", "동기화", "오류만")

    // 네 종류를 다 골라도 "전체"로 바뀌지 않고 고른 상태가 그대로 남아야 한다
    @Test fun diagnosticsAllKinds() = openDiagnostics("diagnostics_all_kinds", "동기화", "로그인·토큰", "잔디 알림", "앱")
}
