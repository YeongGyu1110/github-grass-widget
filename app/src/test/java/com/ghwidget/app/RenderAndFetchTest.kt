package com.ghwidget.app

import android.graphics.Bitmap
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.GitHubClient
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.widget.GraphRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 실제 GitHub 에서 데이터를 받아(네트워크 필요) 파싱을 확인하고,
 * 여러 위젯 크기/테마로 렌더링해 app/build/render-test 에 PNG 로 저장한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderAndFetchTest {

    private val outDir = File("build/render-test").apply { mkdirs() }

    @Test
    fun fetchParseAndRender() {
        val data = GitHubClient.fetch("torvalds", null)
        assertTrue("days=${data.days.size}", data.days.size >= 365)
        assertTrue(data.total > 0)
        assertTrue(data.days.any { it.level > 0 && it.count > 0 })
        println("days=${data.days.size} total=${data.total} first=${data.days.first().date} last=${data.lastDate} streak=${data.currentStreak()}")

        // JSON 캐시 왕복
        val back = ContributionData.fromJson(data.toJson())
        assertEquals(data.days, back.days)

        val base = Settings(username = "torvalds")
        save("4x2_light", GraphRenderer.render(330f, 150f, 2.75f, false, base, data, null))
        save("4x2_dark", GraphRenderer.render(330f, 150f, 2.75f, true, base, data, null))
        save("4x1_dark_noheader", GraphRenderer.render(330f, 70f, 2.75f, true,
            base.copy(showHeader = false), data, null))
        save("2x1_light", GraphRenderer.render(160f, 80f, 2.75f, false, base, data, null))
        save("5x3_purple", GraphRenderer.render(420f, 230f, 2.75f, true,
            base.copy(palette = PaletteId.PURPLE, bgOpacity = 60), data, null))
        save("message", GraphRenderer.render(330f, 150f, 2.75f, false,
            base.copy(username = ""), null, "앱을 열어 GitHub 사용자 이름을 입력하세요"))
    }

    private fun save(name: String, bmp: Bitmap) {
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
