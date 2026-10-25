package com.ghwidget.app

import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.TokenManager
import com.ghwidget.app.data.friendlyError
import com.ghwidget.app.data.isNetworkError
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 짧은 수명 토큰(8시간) + refresh token 처리.
 * 2026-08 부터 새 OAuth App 은 이 방식이 기본이라, 갱신하지 않으면 8시간 뒤 로그아웃된다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenRefreshTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun parsesExpiringTokenResponse() {
        val now = 1_000_000L
        val json = JSONObject(
            """{"access_token":"ghu_a","expires_in":28800,"refresh_token":"ghr_b",
               "refresh_token_expires_in":15897600,"token_type":"bearer","scope":""}""",
        )
        assertEquals(
            GitHubAuth.TokenSet("ghu_a", "ghr_b", now + 28_800_000L, now + 15_897_600_000L),
            GitHubAuth.parseTokens(json, now),
        )
    }

    @Test
    fun parsesRefreshResponses() {
        val ok = GitHubAuth.parseRefresh("""{"access_token":"ghu_new","expires_in":28800,"refresh_token":"ghr_new"}""")
        assertTrue(ok is GitHubAuth.Refresh.Refreshed)
        assertEquals("ghr_new", (ok as GitHubAuth.Refresh.Refreshed).tokens.refreshToken)

        val bad = GitHubAuth.parseRefresh("""{"error":"bad_refresh_token","error_description":"The refresh token passed is incorrect or expired."}""")
        assertTrue(bad is GitHubAuth.Refresh.Invalid)
    }

    @Test
    fun tokenNotDueIsUsedAsIs() {
        val store = Store(context)
        store.signIn(GitHubAuth.TokenSet("ghu_live", "ghr_x", System.currentTimeMillis() + 3_600_000L, null))
        // 만료까지 1시간 남음 → 갱신 없이 그대로 (네트워크를 쓰지 않음)
        assertEquals(TokenManager.Result.Ok("ghu_live"), TokenManager.token(context, store))
    }

    @Test
    fun rejectedLegacyTokenNeedsLogin() {
        val store = Store(context)
        store.signIn(GitHubAuth.TokenSet("gho_legacy", null, null, null))
        // 만료 없는 옛 토큰: 평소엔 그대로 쓰고, 401 로 거부되면 갱신 수단이 없으니 다시 로그인
        assertEquals(TokenManager.Result.Ok("gho_legacy"), TokenManager.token(context, store))
        assertTrue(TokenManager.token(context, store, rejected = "gho_legacy") is TokenManager.Result.NeedLogin)
    }

    @Test
    fun alreadyRefreshedByAnotherJobIsReused() {
        val store = Store(context)
        store.signIn(GitHubAuth.TokenSet("ghu_new", "ghr_new", System.currentTimeMillis() + 3_600_000L, null))
        // 다른 작업이 이미 갱신해서 거부된 토큰과 저장된 토큰이 다르면 새 토큰을 그대로 쓴다
        assertEquals(TokenManager.Result.Ok("ghu_new"), TokenManager.token(context, store, rejected = "ghu_old"))
    }

    @Test
    fun signOutKeepsNoticeAndClearsTokens() {
        val store = Store(context)
        store.signIn(GitHubAuth.TokenSet("ghu_a", "ghr_b", 1L, 2L))
        store.signOut(notice = "로그인 갱신 기한이 지났습니다")
        assertNull(store.accessToken)
        assertNull(store.refreshToken)
        assertEquals("로그인 갱신 기한이 지났습니다", store.signOutNotice)
        // 다시 로그인하면 안내는 지워진다
        store.signIn(GitHubAuth.TokenSet("ghu_c", null, null, null))
        assertNull(store.signOutNotice)
    }

    @Test
    fun refreshReasonSaysExpiredWhenPastExpiry() {
        val expiry = 1_000_000_000L
        assertEquals("401 응답", TokenManager.refreshReason(force = true, expiresAt = expiry, now = expiry - 1))
        // 만료 5분 전 안쪽
        assertTrue(TokenManager.refreshReason(false, expiry, expiry - 60_000L).endsWith("임박"))
        // 동기화 간격 때문에 만료 뒤에 갱신하는 경우
        assertTrue(TokenManager.refreshReason(false, expiry, expiry + 3_600_000L).startsWith("만료됨: "))
    }

    @Test
    fun networkErrorsAreRetryableAndFriendly() {
        val dns = UnknownHostException("Unable to resolve host \"api.github.com\": No address associated with hostname")
        assertTrue(isNetworkError(dns))
        assertTrue(isNetworkError(SocketTimeoutException()))
        assertFalse(isNetworkError(IllegalStateException()))
        assertEquals("Couldn't reach GitHub. Check your internet connection.", friendlyError(context, dns))
    }

    @Test
    @Config(qualifiers = "ko")
    fun friendlyErrorsAreKoreanOnKoreanDevices() {
        assertEquals("인터넷에 연결할 수 없어 GitHub 주소를 찾지 못했습니다", friendlyError(context, UnknownHostException()))
        assertEquals(
            "OAuth App 설정에서 Device Flow가 꺼져 있습니다.",
            GitHubAuth.errorMessage(context, "device_flow_disabled", null),
        )
        // 모르는 오류 코드는 GitHub 가 보낸 설명을 그대로
        assertEquals("Something broke", GitHubAuth.errorMessage(context, "weird_error", "Something broke"))
    }
}
