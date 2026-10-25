package com.ghwidget.app.data

import android.content.Context
import com.ghwidget.app.R
import com.ghwidget.app.data.GitHubClient.FetchException.Kind
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate

/** 로그인한 사용자(viewer) 본인의 contributions 를 GitHub GraphQL API 로 가져온다 */
object GitHubClient {

    /**
     * retryable: 잠시 뒤 다시 시도하면 나아질 수 있는 오류인지 (요청 제한, 서버 오류)
     * unauthorized: 토큰이 취소·만료되어 다시 로그인해야 함
     * message 는 진단 기록용이고, 화면에 보일 문구는 userMessage() 로 만든다
     */
    class FetchException(
        message: String,
        val kind: Kind,
        val code: Int = 0,
        val retryable: Boolean = false,
        val unauthorized: Boolean = false,
    ) : IOException(message) {
        enum class Kind { UNAUTHORIZED, RATE_LIMITED, SERVER, HTTP, GRAPHQL, NO_VIEWER }

        fun userMessage(context: Context): String = when (kind) {
            Kind.UNAUTHORIZED -> context.getString(R.string.error_session_expired)
            Kind.RATE_LIMITED -> context.getString(R.string.error_rate_limited, code)
            Kind.SERVER -> context.getString(R.string.error_server, code)
            Kind.HTTP -> "HTTP $code"
            // GitHub 가 보낸 오류 문구를 그대로 보여준다
            Kind.GRAPHQL -> message ?: context.getString(R.string.error_request_failed)
            Kind.NO_VIEWER -> context.getString(R.string.error_no_viewer)
        }
    }

    private const val QUERY = """
        query {
          viewer {
            login
            avatarUrl(size: 160)
            contributionsCollection {
              contributionCalendar {
                totalContributions
                weeks { contributionDays { date contributionCount contributionLevel } }
              }
            }
          }
        }
    """

    /** avatarUrl: 프로필 이미지 주소 (사진을 바꾸면 주소도 바뀐다) */
    data class Viewer(val data: ContributionData, val avatarUrl: String?)

    fun fetchViewer(token: String): Viewer {
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
            401 -> throw FetchException("로그인이 만료되었습니다. 앱에서 다시 로그인해 주세요.", Kind.UNAUTHORIZED, res.code, unauthorized = true)
            403, 429 -> throw FetchException("요청이 제한되었습니다. 잠시 후 다시 시도합니다 (HTTP ${res.code})", Kind.RATE_LIMITED, res.code, retryable = true)
            in 500..599 -> throw FetchException("GitHub 서버 오류 (HTTP ${res.code})", Kind.SERVER, res.code, retryable = true)
            else -> throw FetchException("HTTP ${res.code}", Kind.HTTP, res.code)
        }
        return parseViewer(res.body)
    }

    internal fun parseViewer(json: String): Viewer {
        val res = JSONObject(json)
        res.optJSONArray("errors")?.let { errs ->
            throw FetchException(errs.optJSONObject(0)?.optString("message") ?: "GraphQL 오류", Kind.GRAPHQL)
        }
        val viewer = res.optJSONObject("data")?.optJSONObject("viewer")
            ?: throw FetchException("GitHub 계정 정보를 받지 못했습니다", Kind.NO_VIEWER)
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
        val data = ContributionData(
            username = viewer.getString("login"),
            days = days,
            total = cal.getInt("totalContributions"),
            fetchedAt = System.currentTimeMillis(),
        )
        return Viewer(data, viewer.optString("avatarUrl").takeIf { it.startsWith("https://") })
    }
}
