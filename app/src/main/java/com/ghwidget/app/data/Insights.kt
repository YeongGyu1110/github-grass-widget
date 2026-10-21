package com.ghwidget.app.data

import java.time.YearMonth

/** 대시보드 통계: 기여 데이터에서 계산한 요약 값들 */
data class Insights(
    /** 기여가 하나라도 있는 날 수 */
    val activeDays: Int,
    /** 기간 전체 하루 평균 기여 수 */
    val dailyAverage: Double,
    /** 가장 많이 기여한 날 (없으면 null) */
    val bestDay: ContributionDay?,
    /** 최근 12개월 월별 합계 (오래된 달 → 최근 달) */
    val monthly: List<Pair<YearMonth, Int>>,
    /** 요일별 합계, 일요일부터 토요일까지 */
    val weekdays: IntArray,
)

fun ContributionData.insights(): Insights {
    val totals = LinkedHashMap<YearMonth, Int>()
    val last = lastDate?.let(YearMonth::from) ?: YearMonth.now()
    for (i in 11 downTo 0) totals[last.minusMonths(i.toLong())] = 0

    val weekdays = IntArray(7)
    for (d in days) {
        val ym = YearMonth.from(d.date)
        totals[ym]?.let { totals[ym] = it + d.count }
        weekdays[d.date.dayOfWeek.value % 7] += d.count
    }
    return Insights(
        activeDays = days.count { it.count > 0 },
        dailyAverage = if (days.isEmpty()) 0.0 else days.sumOf { it.count }.toDouble() / days.size,
        bestDay = days.filter { it.count > 0 }.maxByOrNull { it.count },
        monthly = totals.toList(),
        weekdays = weekdays,
    )
}
