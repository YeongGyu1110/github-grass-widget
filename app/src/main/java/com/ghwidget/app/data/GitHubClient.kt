package com.ghwidget.app.data

import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate

/** 로그인한 사용자(viewer) 본인의 contributions 를 GitHub GraphQL API 로 가져온다 */
object GitHubClient {

    /**
     * retryable: 잠시 뒤 다시 시도하면 나아질 수 있는 오류인지 (요청 제한, 서버 오류)
     * unauthorized: 토큰이 취소·만료되어 다시 로그인해야 함
     */
    class FetchException(
        message: String,
        val retryable: Boolean = false,
        val unauthorized: Boolean = false,
    ) : IOException(message)

    private const val QUERY = """
        query {
          viewer {
            login
            contributionsCollection {
              contributionCalendar {
                totalContributions
                weeks { contributionDays { date contributionCount contributionLevel } }
              }
            }
          }
        }
    """

    fun fetchViewer(token: String): ContributionData {
        val res = Http.post(
            "https://api.github.com/graphql",
            JSONObject().put("query", QUERY).toString(),
            mapOf(
                "Authorization" to "bearer $token",
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            ),
        )
        when (res.code) {
            in 200..299 -> {}
            401 -> throw FetchException("로그인이 만료되었습니다. 앱에서 다시 로그인해 주세요.", unauthorized = true)
            403, 429 -> throw FetchException("요청이 제한되었습니다. 잠시 후 다시 시도합니다 (HTTP ${res.code})", retryable = true)
            in 500..599 -> throw FetchException("GitHub 서버 오류 (HTTP ${res.code})", retryable = true)
            else -> throw FetchException("HTTP ${res.code}")
        }
        return parseViewer(res.body)
    }

    internal fun parseViewer(json: String): ContributionData {
        val res = JSONObject(json)
        res.optJSONArray("errors")?.let { errs ->
            throw FetchException(errs.optJSONObject(0)?.optString("message") ?: "GraphQL 오류")
        }
        val viewer = res.optJSONObject("data")?.optJSONObject("viewer")
            ?: throw FetchException("GitHub 계정 정보를 받지 못했습니다")
        val cal = viewer.getJSONObject("contributionsCollection").getJSONObject("contributionCalendar")

        val days = ArrayList<ContributionDay>(371)
        val weeks = cal.getJSONArray("weeks")
        for (w in 0 until weeks.length()) {
            val ds = weeks.getJSONObject(w).getJSONArray("contributionDays")
            for (i in 0 until ds.length()) {
                val d = ds.getJSONObject(i)
                days += ContributionDay(
                    date = LocalDate.parse(d.getString("date")),
                    count = d.getInt("contributionCount"),
                    level = when (d.getString("contributionLevel")) {
                        "FIRST_QUARTILE" -> 1
                        "SECOND_QUARTILE" -> 2
                        "THIRD_QUARTILE" -> 3
                        "FOURTH_QUARTILE" -> 4
                        else -> 0
                    },
                )
            }
        }
        days.sortBy { it.date }
        return ContributionData(
            username = viewer.getString("login"),
            days = days,
            total = cal.getInt("totalContributions"),
            fetchedAt = System.currentTimeMillis(),
        )
    }
}
