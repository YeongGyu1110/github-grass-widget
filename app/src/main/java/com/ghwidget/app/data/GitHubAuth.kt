package com.ghwidget.app.data

import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * GitHub OAuth Device Flow 로그인.
 * 1) requestDeviceCode() 로 받은 userCode 를 사용자가 verificationUri 에서 입력·승인하고
 * 2) 그동안 앱은 interval 초마다 poll() 해서 액세스 토큰을 받는다.
 * Device Flow 는 Client Secret 이 필요 없어서 앱에 비밀값을 넣지 않아도 된다.
 *
 * scope 는 비워 둔다: 공개 정보 읽기 전용 토큰이며 본인 잔디를 읽는 데 충분하다.
 */
object GitHubAuth {
    const val CLIENT_ID = "Ov23likBdroWbICgn3YU"

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        /** 폴링 간격(초) */
        val interval: Int,
        /** 코드 만료 시각 (epoch ms) */
        val expiresAt: Long,
    ) {
        fun toJson(): String = JSONObject()
            .put("deviceCode", deviceCode).put("userCode", userCode)
            .put("verificationUri", verificationUri).put("interval", interval).put("expiresAt", expiresAt)
            .toString()

        companion object {
            fun fromJson(json: String): DeviceCode = JSONObject(json).run {
                DeviceCode(
                    getString("deviceCode"), getString("userCode"), getString("verificationUri"),
                    getInt("interval"), getLong("expiresAt"),
                )
            }
        }
    }

    /**
     * 로그인으로 받은 토큰 묶음.
     * 2026-08 부터 새 OAuth App 은 기본으로 "짧은 수명 토큰"을 써서 access token 이 8시간 뒤 만료되고,
     * refresh token(6개월)으로 새 묶음을 받아야 한다. 오래된 방식이면 refreshToken·만료 시각이 null.
     */
    data class TokenSet(
        val accessToken: String,
        val refreshToken: String?,
        /** access token 만료 시각 (epoch ms), 만료 없음이면 null */
        val accessExpiresAt: Long?,
        /** refresh token 만료 시각 (epoch ms) */
        val refreshExpiresAt: Long?,
    )

    sealed interface Poll {
        data class Success(val tokens: TokenSet) : Poll
        /** 아직 사용자가 승인하지 않음 */
        data object Pending : Poll
        /** 너무 자주 물어봄: 간격을 늘려야 함 */
        data class SlowDown(val interval: Int) : Poll
        data class Failed(val message: String) : Poll
    }

    fun requestDeviceCode(): DeviceCode {
        val res = postForm("https://github.com/login/device/code", "client_id" to CLIENT_ID, "scope" to "")
        val o = JSONObject(res)
        if (o.has("error")) throw IOException(describeError(o))
        return DeviceCode(
            deviceCode = o.getString("device_code"),
            userCode = o.getString("user_code"),
            verificationUri = o.getString("verification_uri"),
            interval = o.optInt("interval", 5),
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 900) * 1000,
        )
    }

    fun poll(deviceCode: String, currentInterval: Int): Poll = parsePoll(
        postForm(
            "https://github.com/login/oauth/access_token",
            "client_id" to CLIENT_ID,
            "device_code" to deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
        ),
        currentInterval,
    )

    internal fun parsePoll(json: String, currentInterval: Int): Poll {
        val o = JSONObject(json)
        parseTokens(o)?.let { return Poll.Success(it) }
        return when (o.optString("error")) {
            "authorization_pending" -> Poll.Pending
            "slow_down" -> Poll.SlowDown(o.optInt("interval", currentInterval + 5))
            "expired_token" -> Poll.Failed("로그인 코드가 만료되었습니다. 다시 로그인해 주세요.")
            "access_denied" -> Poll.Failed("GitHub에서 로그인을 거부했습니다.")
            else -> Poll.Failed(describeError(o))
        }
    }

    /** 토큰 응답에서 토큰 묶음을 꺼낸다. access_token 이 없으면 null */
    internal fun parseTokens(o: JSONObject, now: Long = System.currentTimeMillis()): TokenSet? {
        val access = o.optString("access_token").takeIf { it.isNotEmpty() } ?: return null
        fun expiry(key: String) = o.optLong(key, 0L).takeIf { it > 0 }?.let { now + it * 1000 }
        return TokenSet(
            accessToken = access,
            refreshToken = o.optString("refresh_token").takeIf { it.isNotEmpty() },
            accessExpiresAt = expiry("expires_in"),
            refreshExpiresAt = expiry("refresh_token_expires_in"),
        )
    }

    sealed interface Refresh {
        data class Refreshed(val tokens: TokenSet) : Refresh
        /** refresh token 도 만료·취소됨: 다시 로그인해야 함 */
        data class Invalid(val message: String) : Refresh
    }

    /**
     * refresh token 으로 새 토큰 묶음을 받는다. Device Flow 로 받은 토큰은 Client Secret 없이 갱신된다.
     * 한 번 쓴 refresh token 과 이전 access token 은 더 이상 쓸 수 없다 (새 묶음을 꼭 저장해야 함).
     * 네트워크 오류는 IOException 으로 던진다.
     */
    fun refresh(refreshToken: String): Refresh = parseRefresh(
        postForm(
            "https://github.com/login/oauth/access_token",
            "client_id" to CLIENT_ID,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        ),
    )

    internal fun parseRefresh(json: String): Refresh {
        val o = JSONObject(json)
        parseTokens(o)?.let { return Refresh.Refreshed(it) }
        return Refresh.Invalid(
            when (o.optString("error")) {
                "bad_refresh_token" -> "로그인 갱신 기한이 지났습니다. 다시 로그인해 주세요."
                else -> describeError(o)
            },
        )
    }

    private fun describeError(o: JSONObject): String = when (o.optString("error")) {
        "device_flow_disabled" -> "OAuth App 설정에서 Device Flow가 꺼져 있습니다."
        "incorrect_client_credentials" -> "OAuth App의 Client ID가 올바르지 않습니다."
        else -> o.optString("error_description").ifEmpty { o.optString("error", "알 수 없는 오류") }
    }

    private fun postForm(url: String, vararg params: Pair<String, String>): String {
        val body = params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val res = Http.post(url, body, mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/x-www-form-urlencoded",
        ))
        if (res.code !in 200..299 && !res.body.trimStart().startsWith("{")) {
            throw IOException("GitHub 로그인 서버 오류 (HTTP ${res.code})")
        }
        return res.body
    }
}
