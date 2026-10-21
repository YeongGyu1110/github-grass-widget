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

    /** retryable: 잠시 뒤 다시 시도하면 나아질 수 있는 오류인지 (요청 제한, 서버 오류) */
    class FetchException(message: String, val retryable: Boolean = false) : IOException(message)

    sealed interface Result {
        /** 마지막으로 받은 뒤 바뀐 것이 없음 (HTTP 304) */
        data object NotModified : Result
        data class Fetched(val data: ContributionData, val etag: String?) : Result
    }

    /** etag: 지난번 HTML 응답의 ETag. 넘기면 바뀐 것이 없을 때 본문 없이 NotModified 를 돌려준다 */
    fun fetch(username: String, token: String?, etag: String? = null): Result =
        if (token.isNullOrBlank()) fetchFromHtml(username, etag)
        else Result.Fetched(fetchFromGraphQl(username, token.trim()), null)

    // ---------------------------------------------------------------- HTML

    private fun fetchFromHtml(username: String, etag: String?): Result {
        val user = URLEncoder.encode(username, "UTF-8")
        val res = request(
            "https://github.com/users/$user/contributions",
            headers = buildMap {
                put("Accept", "text/html")
                if (etag != null) put("If-None-Match", etag)
            },
        )
        if (res.notModified) return Result.NotModified
        return Result.Fetched(parseHtml(username, res.body), res.etag)
    }

    /**
     * 정규식 대신 indexOf 로 한 번만 훑는다 (23만 자 HTML 기준, 정규식은 기기에서 1초 이상 걸렸다).
     *
     * 날짜 칸:  <td ... data-date="2025-10-19" id="contribution-day-component-0-0" data-level="1" ... class="ContributionCalendar-day">
     * 툴팁:     <tool-tip ... for="contribution-day-component-0-0" ...>4 contributions on October 19th.</tool-tip>
     */
    internal fun parseHtml(username: String, html: String): ContributionData {
        val counts = HashMap<String, Int>(512)
        var i = 0
        while (true) {
            val start = html.indexOf("<tool-tip ", i)
            if (start < 0) break
            val tagEnd = html.indexOf('>', start)
            val close = if (tagEnd < 0) -1 else html.indexOf("</tool-tip>", tagEnd)
            if (close < 0) break
            attr(html.substring(start, tagEnd), "for")?.let { counts[it] = leadingCount(html, tagEnd + 1, close) }
            i = close
        }

        val days = ArrayList<ContributionDay>(371)
        i = 0
        while (true) {
            val start = html.indexOf("<td ", i)
            if (start < 0) break
            val end = html.indexOf('>', start)
            if (end < 0) break
            i = end
            val tag = html.substring(start, end)
            if (!tag.contains("ContributionCalendar-day")) continue
            val date = attr(tag, "data-date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            val level = (attr(tag, "data-level")?.toIntOrNull() ?: 0).coerceIn(0, 4)
            // 툴팁이 없으면 레벨만이라도 살린다 (레벨>0 이면 최소 1)
            val count = attr(tag, "id")?.let { counts[it] } ?: if (level > 0) 1 else 0
            days += ContributionDay(date, count, level)
        }
        if (days.isEmpty()) throw FetchException("contributions 를 찾을 수 없습니다 (사용자 이름 확인)")
        days.sortBy { it.date }

        val total = html.indexOf("id=\"js-contribution-activity-description\"")
            .takeIf { it >= 0 }
            ?.let { html.indexOf('>', it) }
            ?.takeIf { it >= 0 }
            ?.let { leadingCount(html, it + 1, minOf(html.length, it + 200)) }
            ?.takeIf { it > 0 }
            ?: days.sumOf { it.count }
        return ContributionData(username, days, total, System.currentTimeMillis())
    }

    /** 태그 문자열에서 ` name="값"` 의 값을 꺼낸다 */
    private fun attr(tag: String, name: String): String? {
        val key = " $name=\""
        val s = tag.indexOf(key)
        if (s < 0) return null
        val v = s + key.length
        val e = tag.indexOf('"', v)
        return if (e < 0) null else tag.substring(v, e)
    }

    /** html[from, until) 이 "  1,234 contribution..." 로 시작하면 1234, 아니면 0 ("No contributions" 등) */
    private fun leadingCount(html: String, from: Int, until: Int): Int {
        var p = from
        while (p < until && html[p].isWhitespace()) p++
        var n = 0
        var digits = 0
        while (p < until) {
            val c = html[p]
            when {
                c in '0'..'9' -> { n = n * 10 + (c - '0'); digits++ }
                c == ',' -> {}
                else -> break
            }
            p++
        }
        if (digits == 0) return 0
        while (p < until && html[p].isWhitespace()) p++
        return if (html.startsWith("contribution", p)) n else 0
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
        ).body)

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

    private class Response(val body: String, val etag: String?, val notModified: Boolean)

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response {
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
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return Response("", null, notModified = true)
            if (code !in 200..299) {
                throw when (code) {
                    401 -> FetchException("토큰이 올바르지 않습니다 (HTTP 401)")
                    404 -> FetchException("사용자를 찾을 수 없습니다 (HTTP 404)")
                    429, 403 -> FetchException("요청이 제한되었습니다. 잠시 후 다시 시도하세요 (HTTP $code)", retryable = true)
                    in 500..599 -> FetchException("GitHub 서버 오류 (HTTP $code)", retryable = true)
                    else -> FetchException("HTTP $code")
                }
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return Response(text, conn.getHeaderField("ETag"), notModified = false)
        } finally {
            conn.disconnect()
        }
    }
}
