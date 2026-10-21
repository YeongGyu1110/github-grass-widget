package com.ghwidget.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.Settings
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 위젯 한 장을 통째로 비트맵으로 그린다: 배경 + 헤더(이름/총합/연속일) + 잔디 그래프.
 * 위젯 크기에 맞춰 칸 크기와 표시할 주(week) 수를 자동으로 정한다.
 */
object GraphRenderer {

    /** 위젯 레이아웃의 click_header 높이(34dp)와 맞춰야 함: PAD + HEADER_H + HEADER_GAP */
    private const val PAD = 12f
    private const val HEADER_H = 16f
    private const val HEADER_GAP = 6f
    private const val MONTH_H = 12f
    private const val GAP_RATIO = 0.22f
    private const val CORNER = 20f
    /** 큰 위젯에서 칸이 너무 커지지 않도록(대신 더 많은 주를 표시) */
    private const val MAX_CELL = 16f

    /** 비트맵 한 장 최대 픽셀 수 (RemoteViews 비트맵 메모리 제한 대비) */
    private const val MAX_PIXELS = 1_200_000f

    fun render(
        widthDp: Float,
        heightDp: Float,
        density: Float,
        dark: Boolean,
        settings: Settings,
        data: ContributionData?,
        message: String?,
    ): Bitmap {
        var d = density
        val pixels = widthDp * d * heightDp * d
        if (pixels > MAX_PIXELS) d *= sqrt(MAX_PIXELS / pixels)

        val w = max(1, (widthDp * d).toInt())
        val h = max(1, (heightDp * d).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pal = Palette.of(settings.palette, dark)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 배경
        val alpha = (settings.bgOpacity.coerceIn(0, 100) * 255 / 100)
        if (alpha > 0) {
            paint.color = pal.background
            paint.alpha = alpha
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), CORNER * d, CORNER * d, paint)
            paint.alpha = 255
        }

        val pad = (if (heightDp < 90f) 8f else PAD) * d
        var top = pad
        val left = pad
        val right = w - pad
        val bottom = h - pad

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)

        if (settings.showHeader && data != null) {
            drawHeader(canvas, text, pal, d, left, right, top, data.username, data)
            top += (HEADER_H + HEADER_GAP) * d
        }

        if (data == null || data.days.isEmpty()) {
            drawMessage(canvas, text, pal, d, left, top, right, bottom, message ?: "불러오는 중…")
            return bmp
        }

        drawGraph(canvas, paint, text, pal, d, settings, data, left, top, right, bottom)
        return bmp
    }

    private fun drawHeader(
        canvas: Canvas, text: TextPaint, pal: Palette, d: Float,
        left: Float, right: Float, top: Float, username: String, data: ContributionData?,
    ) {
        val baseline = top + HEADER_H * d * 0.8f

        text.typeface = Typeface.DEFAULT
        text.textSize = 11f * d
        text.color = pal.textSecondary
        var rightText = ""
        var rightW = 0f
        if (data != null) {
            val nf = NumberFormat.getIntegerInstance()
            val total = nf.format(data.total)
            val streak = data.currentStreak().let { if (it > 0) " · 연속 ${it}일" else "" }
            val maxRight = (right - left) * 0.62f
            rightText = listOf(
                "최근 1년 ${total}회$streak",
                "${total}회$streak",
                "${total}회",
            ).firstOrNull { text.measureText(it) <= maxRight } ?: ""
            rightW = text.measureText(rightText)
            canvas.drawText(rightText, right - rightW, baseline, text)
        }

        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 13f * d
        text.color = pal.textPrimary
        val avail = right - left - rightW - (if (rightText.isEmpty()) 0f else 8f * d)
        canvas.drawText(ellipsize(username, text, avail), left, baseline, text)
    }

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var end = s.length
        while (end > 0 && p.measureText(s, 0, end) + p.measureText("…") > maxW) end--
        return if (end == 0) "" else s.substring(0, end) + "…"
    }

    private fun drawMessage(
        canvas: Canvas, text: TextPaint, pal: Palette, d: Float,
        left: Float, top: Float, right: Float, bottom: Float, message: String,
    ) {
        text.typeface = Typeface.DEFAULT
        text.textSize = 12f * d
        text.color = pal.textSecondary
        val width = max(1, (right - left).toInt())
        val layout = StaticLayout.Builder.obtain(message, 0, message.length, text, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(left, top + max(0f, (bottom - top - layout.height) / 2f))
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawGraph(
        canvas: Canvas, paint: Paint, text: TextPaint, pal: Palette, d: Float,
        settings: Settings, data: ContributionData,
        left: Float, top: Float, right: Float, bottom: Float,
    ) {
        val first = data.days.first().date
        val last = data.lastDate!!
        val lastWeekStart = weekStart(last)
        val maxWeeks = (ChronoUnit.WEEKS.between(weekStart(first), lastWeekStart) + 1).toInt()

        val areaW = right - left
        var monthH = if (settings.showMonths) MONTH_H * d else 0f
        if (bottom - top - monthH < 35f * d) monthH = 0f   // 너무 낮으면 월 표시 생략
        val areaH = bottom - top - monthH
        if (areaW <= 0f || areaH <= 0f) return

        val r = GAP_RATIO
        val sH = min(areaH / (7 + 6 * r), MAX_CELL * d)
        val weeks = floor((areaW + sH * r) / (sH * (1 + r))).toInt().coerceIn(1, maxWeeks)
        val s = min(sH, areaW / (weeks + (weeks - 1) * r))
        val g = s * r
        val gridW = weeks * s + (weeks - 1) * g
        val gridH = 7 * s + 6 * g
        val x0 = left + (areaW - gridW) / 2f
        val y0 = top + monthH + (areaH - gridH) / 2f
        val radius = s * 0.22f

        val firstColStart = lastWeekStart.minusWeeks((weeks - 1).toLong())
        val cell = RectF()
        for (c in 0 until weeks) {
            val colStart = firstColStart.plusWeeks(c.toLong())
            val x = x0 + c * (s + g)
            for (row in 0 until 7) {
                val date = colStart.plusDays(row.toLong())
                if (date.isAfter(last) || date.isBefore(first)) continue
                val level = data.byDate[date]?.level ?: 0
                paint.color = pal.levels[level]
                val y = y0 + row * (s + g)
                cell.set(x, y, x + s, y + s)
                canvas.drawRoundRect(cell, radius, radius, paint)
            }
        }

        if (monthH > 0f) drawMonths(canvas, text, pal, d, firstColStart, weeks, x0, s + g, y0 - 3f * d)
    }

    private fun drawMonths(
        canvas: Canvas, text: TextPaint, pal: Palette, d: Float,
        firstColStart: LocalDate, weeks: Int, x0: Float, step: Float, baseline: Float,
    ) {
        text.typeface = Typeface.DEFAULT
        text.textSize = 9f * d
        text.color = pal.textSecondary

        // 각 열의 첫날(일요일) 기준으로 월이 바뀌는 열을 후보로
        val candidates = ArrayList<Pair<Int, LocalDate>>()
        for (c in 0 until weeks) {
            val date = firstColStart.plusWeeks(c.toLong())
            if (c == 0 || date.month != date.minusWeeks(1).month) candidates += c to date
        }
        // 첫 열의 월이 바로 다음에 바뀌면 라벨이 겹치므로 생략
        if (candidates.size > 1 && candidates[1].first - candidates[0].first < 3) candidates.removeAt(0)

        val locale = Locale.getDefault()
        var lastEnd = -Float.MAX_VALUE
        for ((c, date) in candidates) {
            val label = date.month.getDisplayName(TextStyle.SHORT, locale)
            val x = x0 + c * step
            val lw = text.measureText(label)
            if (x < lastEnd + 4f * d || x + lw > canvas.width) continue
            canvas.drawText(label, x, baseline, text)
            lastEnd = x + lw
        }
    }

    /** GitHub 처럼 일요일 시작 주 */
    private fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value % 7).toLong())
}
