package com.ghwidget.app

import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.GitHubClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import java.time.LocalDate

/**
 * indexOf 기반 파서가 예전 정규식 파서와 같은 결과를 내는지 실제 GitHub HTML 로 비교한다 (네트워크 필요).
 * 순수 JVM 테스트라 Robolectric 이 필요 없다.
 */
class HtmlParserTest {

    @Test
    fun matchesRegexParserOnLiveHtml() {
        for (user in listOf("torvalds", "octocat")) {
            val html = URL("https://github.com/users/$user/contributions").readText()
            val fast = GitHubClient.parseHtml(user, html)
            val (refDays, refTotal) = regexParse(html)

            assertEquals("$user days", refDays, fast.days)
            assertEquals("$user total", refTotal, fast.total)
            assertTrue(fast.days.size >= 365)

            val t0 = System.nanoTime()
            repeat(20) { GitHubClient.parseHtml(user, html) }
            println("$user: days=${fast.days.size} total=${fast.total} " +
                "parse avg=${(System.nanoTime() - t0) / 20 / 1_000}us (JVM)")
        }
    }

    @Test
    fun parsesCountsAndNoContributions() {
        val html = """
            <h2 id="js-contribution-activity-description" class="f4">
              1,234
              contributions
            </h2>
            <td tabindex="0" data-date="2026-01-04" id="c-0-0" data-level="0" class="ContributionCalendar-day"></td>
            <td tabindex="0" data-date="2026-01-05" id="c-1-0" data-level="4" class="ContributionCalendar-day"></td>
            <td class="ContributionCalendar-label">Mon</td>
            <tool-tip for="c-0-0" class="sr-only">No contributions on January 4th.</tool-tip>
            <tool-tip for="c-1-0" class="sr-only">1,005 contributions on January 5th.</tool-tip>
        """.trimIndent()
        val d = GitHubClient.parseHtml("x", html)
        assertEquals(
            listOf(
                ContributionDay(LocalDate.parse("2026-01-04"), 0, 0),
                ContributionDay(LocalDate.parse("2026-01-05"), 1005, 4),
            ),
            d.days,
        )
        assertEquals(1234, d.total)
    }

    /** 1.0.1 까지 쓰던 정규식 파서 (비교 기준) */
    private fun regexParse(html: String): Pair<List<ContributionDay>, Int> {
        val tdRegex = Regex("""<td\b[^>]*\bContributionCalendar-day\b[^>]*>""")
        val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
        val tooltipRegex = Regex("""<tool-tip\b[^>]*\bfor="([^"]+)"[^>]*>([^<]*)</tool-tip>""")
        val countRegex = Regex("""^\s*([\d,]+)\s+contribution""")
        val totalRegex = Regex("""id="js-contribution-activity-description"[^>]*>\s*([\d,]+)\s+contribution""")

        val counts = HashMap<String, Int>()
        tooltipRegex.findAll(html).forEach { m ->
            counts[m.groupValues[1]] = countRegex.find(m.groupValues[2])?.groupValues?.get(1)
                ?.replace(",", "")?.toIntOrNull() ?: 0
        }
        val days = tdRegex.findAll(html).mapNotNull { m ->
            val attrs = attrRegex.findAll(m.value).associate { it.groupValues[1] to it.groupValues[2] }
            val date = attrs["data-date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val level = attrs["data-level"]?.toIntOrNull() ?: 0
            val count = attrs["id"]?.let { counts[it] } ?: if (level > 0) 1 else 0
            ContributionDay(date, count, level.coerceIn(0, 4))
        }.sortedBy { it.date }.toList()
        val total = totalRegex.find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            ?: days.sumOf { it.count }
        return days to total
    }
}
