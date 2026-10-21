package com.ghwidget.app

import android.graphics.Bitmap
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.widget.GraphRenderer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** 여러 위젯 크기/테마로 렌더링해 app/build/render-test 에 PNG 로 저장한다 (눈으로 확인용) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {

    private val outDir = File("build/render-test").apply { mkdirs() }

    /** 1년치 가짜 데이터 (항상 같은 결과가 나오도록 고정 시드) */
    private fun sampleData(): ContributionData {
        val rnd = Random(42)
        val today = LocalDate.now()
        val days = (0L until 371L).map { i ->
            val level = (rnd.nextInt(9) - 4).coerceIn(0, 4)
            ContributionDay(today.minusDays(370 - i), if (level == 0) 0 else level * 3 + rnd.nextInt(3), level)
        }
        return ContributionData("octocat", days, days.sumOf { it.count }, System.currentTimeMillis())
    }

    @Test
    fun renderVariants() {
        val data = sampleData()
        // JSON 캐시 왕복
        assertEquals(data.days, ContributionData.fromJson(data.toJson()).days)

        val base = Settings()
        save("4x2_light", GraphRenderer.render(330f, 150f, 2.75f, false, base, data, null))
        save("4x2_dark", GraphRenderer.render(330f, 150f, 2.75f, true, base, data, null))
        save("4x1_dark_noheader", GraphRenderer.render(330f, 70f, 2.75f, true,
            base.copy(showHeader = false), data, null))
        save("2x1_light", GraphRenderer.render(160f, 80f, 2.75f, false, base, data, null))
        save("5x3_purple", GraphRenderer.render(420f, 230f, 2.75f, true,
            base.copy(palette = PaletteId.PURPLE, bgOpacity = 60), data, null))
        save("message", GraphRenderer.render(330f, 150f, 2.75f, false,
            base, null, "앱을 열어 GitHub에 로그인하세요"))
    }

    private fun save(name: String, bmp: Bitmap) {
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
