package com.ghwidget.app.data

import android.content.Context
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * 쓸 수 있는 access token 을 돌려준다. 만료가 가까우면(5분 전) refresh token 으로 먼저 갱신한다.
 * refresh token 은 한 번 쓰면 무효가 되므로, 두 작업이 동시에 갱신하지 않도록 한 번에 하나만 실행한다.
 */
object TokenManager {
    private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
    private val lock = Any()

    sealed interface Result {
        data class Ok(val token: String) : Result
        /** 다시 로그인해야 함 (refresh token 없음/만료/취소) */
        data class NeedLogin(val reason: String) : Result
    }

    /**
     * rejected: API 가 401 로 거부한 토큰. 주면 만료 시각과 상관없이 갱신한다.
     * 네트워크 오류는 IOException 으로 던진다 (토큰은 그대로 둔다).
     */
    fun token(context: Context, store: Store, rejected: String? = null): Result = synchronized(lock) {
        val token = store.accessToken ?: return Result.NeedLogin("로그인 정보가 없습니다")
        // 다른 작업이 그사이 이미 갱신했으면 새 토큰을 그대로 쓴다
        if (rejected != null && token != rejected) return Result.Ok(token)
        val force = rejected != null
        val expiresAt = store.accessExpiresAt
        val now = System.currentTimeMillis()
        val due = force || (expiresAt > 0 && now > expiresAt - REFRESH_MARGIN_MS)
        if (!due) return Result.Ok(token)

        val refreshToken = store.refreshToken
            ?: return if (force) Result.NeedLogin("GitHub가 로그인 토큰을 거부했습니다 (HTTP 401)") else Result.Ok(token)

        DiagLog.log(context, "토큰 갱신 시도 (${refreshReason(force, expiresAt, now)})")
        return when (val r = GitHubAuth.refresh(refreshToken)) {
            is GitHubAuth.Refresh.Refreshed -> {
                store.saveTokens(r.tokens)
                DiagLog.log(context, "토큰 갱신 성공 → 다음 만료 ${r.tokens.accessExpiresAt?.let(::time) ?: "없음"}")
                Result.Ok(r.tokens.accessToken)
            }
            is GitHubAuth.Refresh.Invalid -> {
                DiagLog.log(context, "토큰 갱신 실패: ${r.message}")
                Result.NeedLogin(r.message)
            }
        }
    }

    /** 갱신 이유 (기록용). 동기화 간격 때문에 만료 시각이 이미 지난 뒤에 갱신하는 경우가 많다 */
    internal fun refreshReason(force: Boolean, expiresAt: Long, now: Long): String = when {
        force -> "401 응답"
        now >= expiresAt -> "만료됨: ${time(expiresAt)}"
        else -> "만료 ${time(expiresAt)} 임박"
    }

    fun time(epochMs: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(epochMs))
}

/** 인터넷 연결 문제로 생긴 예외인지 (잠깐 뒤 다시 시도할 만한 오류) */
fun isNetworkError(e: Throwable): Boolean = e is UnknownHostException || e is ConnectException ||
    e is SocketTimeoutException || e is NoRouteToHostException || e is SocketException ||
    e is SSLException || e is InterruptedIOException

/** 사용자에게 보여줄 오류 문구 */
fun friendlyError(e: Throwable): String = when (e) {
    is UnknownHostException -> "인터넷에 연결할 수 없어 GitHub 주소를 찾지 못했습니다"
    is SocketTimeoutException -> "GitHub 응답이 늦어 시간이 초과되었습니다"
    is ConnectException, is NoRouteToHostException -> "GitHub에 연결하지 못했습니다"
    is SSLException -> "보안 연결을 만들지 못했습니다 (네트워크 확인 필요)"
    is GitHubClient.FetchException -> e.message ?: "GitHub 요청 실패"
    else -> e.message ?: e.javaClass.simpleName
}
