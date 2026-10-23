package com.ghwidget.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.Settings
import com.ghwidget.app.ui.Account
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
@Config(sdk = [34], qualifiers = "w400dp-h1700dp-xhdpi")
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
        versionName = "v1.7.0",
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

    @Test fun loadingLight() = shoot("loading_light", false, base.copy(account = Account.SignedIn("octocat"), refreshing = true))
}
