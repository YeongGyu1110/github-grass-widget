package com.ghwidget.app.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.R
import com.ghwidget.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.format.TextStyle as JTextStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.flow.first

private fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

/**
 * 1년치 잔디 그래프. 가로로 스크롤되며 처음에는 가장 최근 주가 보이도록 끝으로 이동한다.
 * 칸을 누르면 onSelect 로 그날의 기여 정보를 알려준다.
 */
@Composable
fun ContributionHeatmap(
    data: ContributionData,
    levels: IntArray,
    selected: LocalDate?,
    onSelect: (ContributionDay?) -> Unit,
) {
    val c = AppTheme.colors
    val cell = 13.dp
    val gap = 3.dp
    val monthH = 18.dp
    // 선택 테두리는 칸 밖으로 (간격 + 선 두께/2)만큼 나온다. 가로 스크롤 영역은 경계 밖을 잘라내므로
    // 맨 왼쪽·오른쪽 열(1년 전 주·이번 주)의 테두리가 잘리지 않게 그만큼 좌우 여백을 둔다
    val outlineInset = 1.dp
    val outlineWidth = 1.5.dp
    val outlineOverhang = outlineInset + outlineWidth / 2
    val first = weekStart(data.days.first().date)
    val last = data.lastDate!!
    val weeks = (ChronoUnit.WEEKS.between(first, weekStart(last)) + 1).toInt()
    val width = cell * weeks + gap * (weeks - 1)
    val height = monthH + cell * 7 + gap * 6

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = c.mutedForeground)
    val scroll = rememberScrollState()
    LaunchedEffect(data.days.size) {
        snapshotFlow { scroll.maxValue }.first { it > 0 && it < Int.MAX_VALUE }
        scroll.scrollTo(scroll.maxValue)
    }

    Row {
        // 요일 라벨 (월·수·금)
        // 그래프 쪽 여백만큼 줄여서 라벨과 첫 열 사이 간격은 그대로
        Column(Modifier.padding(top = monthH, end = 6.dp - outlineOverhang), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until 7) {
                Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                    if (row % 2 == 1) {
                        Text(
                            DayOfWeek.of(if (row == 0) 7 else row).getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                            style = labelStyle,
                        )
                    }
                }
            }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .padding(horizontal = outlineOverhang)
                    .size(width, height)
                    .pointerInput(data) {
                        detectTapGestures { o ->
                            val step = (cell + gap).toPx()
                            val col = (o.x / step).toInt()
                            val row = ((o.y - monthH.toPx()) / step).toInt()
                            if (o.y < monthH.toPx() || row !in 0..6) return@detectTapGestures
                            val date = first.plusWeeks(col.toLong()).plusDays(row.toLong())
                            onSelect(data.byDate[date])
                        }
                    },
            ) {
                val cellPx = cell.toPx()
                val step = (cell + gap).toPx()
                val top = monthH.toPx()
                val radius = CornerRadius(cellPx * 0.22f)

                // 월 라벨: 월이 바뀌는 열 위에, 이전 라벨과 겹치지 않을 때만
                var lastLabelEnd = -1f
                for (col in 0 until weeks) {
                    val d = first.plusWeeks(col.toLong())
                    if (col != 0 && d.month == d.minusWeeks(1).month) continue
                    if (col == 0 && d.dayOfMonth > 21) continue
                    val label = measurer.measure(d.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()), labelStyle)
                    val x = col * step
                    if (x < lastLabelEnd + 6f) continue
                    drawText(label, topLeft = Offset(x, 0f))
                    lastLabelEnd = x + label.size.width
                }

                for (col in 0 until weeks) {
                    for (row in 0 until 7) {
                        val date = first.plusWeeks(col.toLong()).plusDays(row.toLong())
                        if (date.isAfter(last)) continue
                        val day = data.byDate[date]
                        val topLeft = Offset(col * step, top + row * step)
                        drawRoundRect(Color(levels[day?.level ?: 0]), topLeft, Size(cellPx, cellPx), radius)
                        if (date == selected) {
                            val inset = outlineInset.toPx()
                            drawRoundRect(
                                c.foreground,
                                topLeft - Offset(inset, inset),
                                Size(cellPx + inset * 2, cellPx + inset * 2),
                                CornerRadius(cellPx * 0.3f),
                                style = Stroke(width = outlineWidth.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "적음 ■■■■■ 많음" 범례 */
@Composable
fun HeatmapLegend(levels: IntArray) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.legend_less), style = MaterialTheme.typography.labelSmall, color = c.mutedForeground)
        Spacer(Modifier.width(2.dp))
        levels.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(it))) }
        Spacer(Modifier.width(2.dp))
        Text(stringResource(R.string.legend_more), style = MaterialTheme.typography.labelSmall, color = c.mutedForeground)
    }
}

/** 최근 12개월 월별 기여 막대 그래프. 이번 달은 진한 색으로 강조 */
@Composable
fun MonthlyBars(monthly: List<Pair<YearMonth, Int>>, barColor: Color, highlightColor: Color) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = c.mutedForeground)
    val valueStyle = TextStyle(fontSize = 10.sp, color = c.foreground, fontWeight = FontWeight.Medium)
    val maxValue = (monthly.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelH = 18.dp.toPx()
        val valueH = 16.dp.toPx()
        val chartH = size.height - labelH - valueH
        val slot = size.width / monthly.size
        val barW = slot * 0.56f
        // 기준선
        drawLine(c.border, Offset(0f, valueH + chartH), Offset(size.width, valueH + chartH), 1.dp.toPx())
        monthly.forEachIndexed { i, (ym, value) ->
            val isLast = i == monthly.lastIndex
            val h = if (value == 0) 0f else (chartH * value / maxValue).coerceAtLeast(3.dp.toPx())
            val x = i * slot + (slot - barW) / 2
            val y = valueH + chartH - h
            if (h > 0f) {
                drawRoundRect(
                    if (isLast) highlightColor else barColor,
                    Offset(x, y), Size(barW, h),
                    CornerRadius(4.dp.toPx()),
                )
            }
            if (value == maxValue || isLast) {
                val t = measurer.measure(value.toString(), valueStyle)
                drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2, y - t.size.height - 2.dp.toPx()))
            }
            val label = measurer.measure(ym.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()), labelStyle)
            drawText(label, topLeft = Offset(i * slot + slot / 2 - label.size.width / 2, size.height - label.size.height))
        }
    }
}

/** 요일별 기여 비율 막대 (일~토) */
@Composable
fun WeekdayBars(weekdays: IntArray, barColor: Color, highlightColor: Color) {
    val c = AppTheme.colors
    val total = weekdays.sum().coerceAtLeast(1)
    val max = weekdays.maxOrNull()?.coerceAtLeast(1) ?: 1
    val names = (0 until 7).map { DayOfWeek.of(if (it == 0) 7 else it).getDisplayName(JTextStyle.SHORT, Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        weekdays.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names[i], style = MaterialTheme.typography.labelMedium, color = c.mutedForeground, modifier = Modifier.width(28.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.muted)) {
                    Box(
                        Modifier
                            .fillMaxWidth(v.toFloat() / max)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (v == max) highlightColor else barColor),
                    )
                }
                Text(
                    "${(v * 100f / total).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.foreground,
                    modifier = Modifier.width(44.dp).padding(start = 8.dp),
                )
            }
        }
    }
}
