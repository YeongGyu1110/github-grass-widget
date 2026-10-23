package com.ghwidget.app

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.PaletteId
import com.ghwidget.app.data.Settings
import com.ghwidget.app.widget.GraphRenderer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    /** 앱 아이콘(적응형)과 테마 아이콘용 단색 그림을 둥근 사각형 마스크로 그려 본다 */
    @Test
    fun renderLauncherIcon() {
        val ctx = RuntimeEnvironment.getApplication()
        val icon = ctx.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        save("launcher_icon", masked(192) { c, size ->
            // 적응형 아이콘은 108dp 중 가운데 72dp 만 보이므로 1.5배로 그려 가장자리를 잘라낸다
            val extra = size / 4
            icon.background.setBounds(-extra, -extra, size + extra, size + extra)
            icon.foreground.setBounds(-extra, -extra, size + extra, size + extra)
            icon.background.draw(c)
            icon.foreground.draw(c)
        })
        save("launcher_icon_themed", masked(192) { c, size ->
            c.drawColor(0xFFD7E8D3.toInt())
            val mono = icon.monochrome!!.mutate()
            mono.setTint(0xFF1F3B22.toInt())
            val extra = size / 4
            mono.setBounds(-extra, -extra, size + extra, size + extra)
            mono.draw(c)
        })
    }

    private fun masked(size: Int, draw: (Canvas, Int) -> Unit): Bitmap {
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        draw(Canvas(layer), size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(layer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.28f, size * 0.28f, paint)
        return out
    }

    private fun save(name: String, bmp: Bitmap) {
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
