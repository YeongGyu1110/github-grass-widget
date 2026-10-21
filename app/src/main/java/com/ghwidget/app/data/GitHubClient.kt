package com.ghwidget.app.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * contributions 데이터를 가져온다.
 * - 토큰이 있으면 GraphQL API (공식, 안정적)
 * - 없으면 github.com/users/{name}/contributions HTML 을 파싱 (토큰 불필요)
 */
object GitHubClient {

    class FetchException(message: String) : IOException(message)

    fun fetch(username: String, token: String?): ContributionData =
        if (token.isNullOrBlank()) fetchFromHtml(username) else fetchFromGraphQl(username, token.trim())

    // ---------------------------------------------------------------- HTML

    private val tdRegex = Regex("""<td\b[^>]*\bContributionCalendar-day\b[^>]*>""")
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val tooltipRegex = Regex("""<tool-tip\b[^>]*\bfor="([^"]+)"[^>]*>([^<]*)</tool-tip>""")
    private val countRegex = Regex("""^\s*([\d,]+)\s+contribution""")
    private val totalRegex =
        Regex("""id="js-contribution-activity-description"[^>]*>\s*([\d,]+)\s+contribution""")

    private fun fetchFromHtml(username: String): ContributionData {
        val user = URLEncoder.encode(username, "UTF-8")
        val html = request("https://github.com/users/$user/contributions", headers = mapOf(
            "Accept" to "text/html",
        ))

        val counts = HashMap<String, Int>()
        tooltipRegex.findAll(html).forEach { m ->
            val text = m.groupValues[2]
            counts[m.groupValues[1]] = countRegex.find(text)?.groupValues?.get(1)
                ?.replace(",", "")?.toIntOrNull() ?: 0
        }

        val days = tdRegex.findAll(html).mapNotNull { m ->
            val attrs = attrRegex.findAll(m.value).associate { it.groupValues[1] to it.groupValues[2] }
            val date = attrs["data-date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val level = attrs["data-level"]?.toIntOrNull() ?: 0
            // 툴팁이 없으면 레벨만이라도 살린다 (레벨>0 이면 최소 1)
            val count = attrs["id"]?.let { counts[it] } ?: if (level > 0) 1 else 0
            ContributionDay(date, count, level.coerceIn(0, 4))
        }.sortedBy { it.date }.toList()

        if (days.isEmpty()) throw FetchException("contributions 를 찾을 수 없습니다 (사용자 이름 확인)")

        val total = totalRegex.find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            ?: days.sumOf { it.count }
        return ContributionData(username, days, total, System.currentTimeMillis())
    }

    // ------------------------------------------------------------- GraphQL

    private const val QUERY = """
        query(${'$'}login: String!) {
          user(login: ${'$'}login) {
            contributionsCollection {
              contributionCalendar {
                totalContributions
                weeks { contributionDays { date contributionCount contributionLevel } }
              }
            }
          }
        }
    """

    private fun fetchFromGraphQl(username: String, token: String): ContributionData {
        val body = JSONObject()
            .put("query", QUERY)
            .put("variables", JSONObject().put("login", username))
            .toString()
        val res = JSONObject(request(
            "https://api.github.com/graphql",
            method = "POST",
            body = body,
            headers = mapOf(
                "Authorization" to "bearer $token",
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            ),
        ))

        res.optJSONArray("errors")?.let { errs ->
            throw FetchException(errs.optJSONObject(0)?.optString("message") ?: "GraphQL 오류")
        }
        val user = res.optJSONObject("data")?.optJSONObject("user")
            ?: throw FetchException("사용자 '$username' 을(를) 찾을 수 없습니다")
        val cal = user.getJSONObject("contributionsCollection").getJSONObject("contributionCalendar")

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
        return ContributionData(username, days, cal.getInt("totalContributions"), System.currentTimeMillis())
    }

    // --------------------------------------------------------------- HTTP

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "GithubContributionsWidget/1.0 (Android)")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw FetchException(when (code) {
                    401 -> "토큰이 올바르지 않습니다 (HTTP 401)"
                    404 -> "사용자를 찾을 수 없습니다 (HTTP 404)"
                    429, 403 -> "요청이 제한되었습니다. 잠시 후 다시 시도하세요 (HTTP $code)"
                    else -> "HTTP $code"
                })
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
