package com.ghwidget.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 하루치 기여 정보. level 은 GitHub 의 0(없음)~4(가장 많음) 단계. */
data class ContributionDay(
    val date: LocalDate,
    val count: Int,
    val level: Int,
)

data class ContributionData(
    val username: String,
    /** 날짜 오름차순 */
    val days: List<ContributionDay>,
    /** GitHub 가 표시하는 최근 1년 총 기여 수 */
    val total: Int,
    val fetchedAt: Long,
) {
    val byDate: Map<LocalDate, ContributionDay> by lazy { days.associateBy { it.date } }

    val lastDate: LocalDate? get() = days.lastOrNull()?.date

    /** 오늘(또는 오늘 기여가 아직 없다면 어제)부터 거꾸로 이어지는 연속 기여 일수 */
    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        var d = today
        if ((byDate[d]?.count ?: 0) == 0) d = d.minusDays(1)
        var streak = 0
        while ((byDate[d]?.count ?: 0) > 0) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    fun toJson(): String = JSONObject().apply {
        put("username", username)
        put("total", total)
        put("fetchedAt", fetchedAt)
        put("days", JSONArray().apply {
            days.forEach { put(JSONArray().put(it.date.toString()).put(it.count).put(it.level)) }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): ContributionData {
            val o = JSONObject(json)
            val arr = o.getJSONArray("days")
            val days = (0 until arr.length()).map {
                val d = arr.getJSONArray(it)
                ContributionDay(LocalDate.parse(d.getString(0)), d.getInt(1), d.getInt(2))
            }
            return ContributionData(
                username = o.getString("username"),
                days = days,
                total = o.getInt("total"),
                fetchedAt = o.getLong("fetchedAt"),
            )
        }
    }
}
