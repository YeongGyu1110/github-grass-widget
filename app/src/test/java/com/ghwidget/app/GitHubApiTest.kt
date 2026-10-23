package com.ghwidget.app

import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.GitHubAuth.Poll
import com.ghwidget.app.data.GitHubClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 로그인(Device Flow) 응답 처리와 GraphQL 응답 파싱. org.json 이 필요해서 Robolectric 으로 실행 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GitHubApiTest {

    @Test
    fun pollResponses() {
        // 오래된 방식(만료 없는 토큰)
        assertEquals(
            Poll.Success(GitHubAuth.TokenSet("gho_abc", null, null, null)),
            GitHubAuth.parsePoll("""{"access_token":"gho_abc","token_type":"bearer","scope":""}""", 5),
        )
        assertEquals(Poll.Pending, GitHubAuth.parsePoll("""{"error":"authorization_pending"}""", 5))
        assertEquals(Poll.SlowDown(10), GitHubAuth.parsePoll("""{"error":"slow_down","interval":10}""", 5))
        assertEquals(Poll.SlowDown(10), GitHubAuth.parsePoll("""{"error":"slow_down"}""", 5))
        assertTrue(GitHubAuth.parsePoll("""{"error":"expired_token"}""", 5) is Poll.Failed)
        assertTrue(GitHubAuth.parsePoll("""{"error":"access_denied"}""", 5) is Poll.Failed)
        assertEquals(
            Poll.Failed("OAuth App 설정에서 Device Flow가 꺼져 있습니다."),
            GitHubAuth.parsePoll("""{"error":"device_flow_disabled"}""", 5),
        )
    }

    @Test
    fun deviceCodeSurvivesSaveAndRestore() {
        val code = GitHubAuth.DeviceCode("dc", "ABCD-1234", "https://github.com/login/device", 5, 123L)
        assertEquals(code, GitHubAuth.DeviceCode.fromJson(code.toJson()))
    }

    @Test
    fun parsesViewerCalendar() {
        val json = """
            {"data":{"viewer":{"login":"octocat","avatarUrl":"https://avatars.githubusercontent.com/u/583231?s=160&v=4","contributionsCollection":{"contributionCalendar":{
              "totalContributions":7,
              "weeks":[
                {"contributionDays":[
                  {"date":"2026-01-05","contributionCount":5,"contributionLevel":"FOURTH_QUARTILE"},
                  {"date":"2026-01-04","contributionCount":0,"contributionLevel":"NONE"}]},
                {"contributionDays":[
                  {"date":"2026-01-11","contributionCount":2,"contributionLevel":"SECOND_QUARTILE"}]}
              ]}}}}}
        """.trimIndent()
        val viewer = GitHubClient.parseViewer(json)
        assertEquals("https://avatars.githubusercontent.com/u/583231?s=160&v=4", viewer.avatarUrl)
        val data = viewer.data
        assertEquals("octocat", data.username)
        assertEquals(7, data.total)
        assertEquals(
            listOf(
                ContributionDay(LocalDate.parse("2026-01-04"), 0, 0),
                ContributionDay(LocalDate.parse("2026-01-05"), 5, 4),
                ContributionDay(LocalDate.parse("2026-01-11"), 2, 2),
            ),
            data.days,
        )
    }

    @Test(expected = GitHubClient.FetchException::class)
    fun graphQlErrorBecomesFetchException() {
        GitHubClient.parseViewer("""{"errors":[{"message":"Something went wrong"}]}""")
    }

    /** 실제 토큰으로 확인하고 싶을 때만: 환경 변수 GITHUB_TOKEN 이 있으면 실행 */
    @Test
    fun liveFetchWithToken() {
        val token = System.getenv("GITHUB_TOKEN")
        assumeTrue("GITHUB_TOKEN 없음 - 건너뜀", !token.isNullOrBlank())
        val data = GitHubClient.fetchViewer(token!!).data
        assertTrue(data.days.size >= 365)
        println("login=${data.username} days=${data.days.size} total=${data.total}")
    }

    /** 공개 프로필 이미지를 실제로 받아 PNG/JPEG 인지 확인 */
    @Test
    fun downloadsAvatarImage() {
        val bytes = com.ghwidget.app.data.Http.getBytes("https://avatars.githubusercontent.com/u/583231?s=160&v=4")
        val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        val jpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        assertTrue("이미지 형식이 아님 (${bytes.size}B)", png || jpeg)
        println("avatar bytes=${bytes.size} ${if (png) "png" else "jpeg"}")
    }

    /** 실제 Client ID 로 로그인 코드를 받을 수 있는지 (OAuth App 에 Device Flow 가 켜져 있는지) */
    @Test
    fun deviceFlowEnabledForClientId() {
        val code = GitHubAuth.requestDeviceCode()
        assertTrue(code.userCode.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")))
        assertEquals(Poll.Pending, GitHubAuth.poll(code.deviceCode, code.interval))
    }
}
