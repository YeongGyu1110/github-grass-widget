package com.ghwidget.app.work

import android.content.Context
import com.ghwidget.app.R
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.GitHubClient
import com.ghwidget.app.data.Http
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.TokenManager
import com.ghwidget.app.data.friendlyError
import com.ghwidget.app.data.isNetworkError
import com.ghwidget.app.reminder.Reminders
import com.ghwidget.app.widget.WidgetUpdater
import java.io.IOException

/**
 * 동기화 한 번: 토큰 확인(필요하면 갱신) → contributions 받기 → 저장 → 바뀌었으면 위젯 다시 그리기.
 * 주기 갱신(RefreshWorker)과 잔디 알림 확인(ReminderWorker)이 함께 쓴다.
 */
object Sync {

    sealed interface Outcome {
        data class Success(val changed: Boolean) : Outcome
        /** retryable: 잠시 뒤 다시 하면 나아질 수 있는 오류 (인터넷·요청 제한·서버 오류) */
        data class Failed(val retryable: Boolean) : Outcome
        /** 토큰을 갱신할 수 없어 로그아웃됨 */
        data object SignedOut : Outcome
        data object NotSignedIn : Outcome
    }

    /** willRetry: 실패하면 호출한 쪽이 다시 시도할지 (기록용) */
    fun run(context: Context, reason: String, attempt: Int = 0, willRetry: Boolean = false): Outcome {
        val ctx = context.applicationContext
        val store = Store(ctx)
        if (store.accessToken == null) {
            WidgetUpdater.updateAll(ctx)
            return Outcome.NotSignedIn
        }
        DiagLog.log(ctx, "동기화 시작: $reason (시도 ${attempt + 1}, 네트워크: ${DiagLog.network(ctx)})")

        store.lastAttemptAt = System.currentTimeMillis()
        val cached = store.cachedData()
        return try {
            val viewer = fetchWithAuth(ctx, store) ?: return Outcome.SignedOut
            val data = viewer.data
            store.saveData(data)
            // 프로필 이미지는 주소가 바뀌었을 때만(=사진을 바꿨을 때만) 받는다. 실패해도 동기화는 성공으로 둔다
            val avatarUrl = viewer.avatarUrl
            if (avatarUrl != null && store.needsAvatar(avatarUrl)) {
                runCatching { store.saveAvatar(avatarUrl, Http.getBytes(avatarUrl)) }
            }
            // 그래프 내용이 같으면 다시 그리지 않는다 (런처로 비트맵을 보내는 비용 절약)
            val changed = cached == null || cached.days != data.days || cached.total != data.total
            if (changed) WidgetUpdater.updateAll(ctx)
            // 오늘 기여가 생겼으면 떠 있는 잔디 알림을 지운다
            if (Reminders.todayCount(data) > 0) Reminders.clearNotification(ctx)
            DiagLog.log(ctx, "동기화 성공 (${if (changed) "그래프 바뀜 → 위젯 다시 그림" else "변화 없음"})")
            Outcome.Success(changed)
        } catch (e: GitHubClient.FetchException) {
            failure(ctx, store, cached == null, e, e.retryable, willRetry)
        } catch (e: Exception) {
            failure(ctx, store, cached == null, e, isNetworkError(e), willRetry)
        }
    }

    /**
     * 토큰을 확인(필요하면 갱신)해서 요청한다. 401 이면 토큰을 갱신해 한 번 더 시도.
     * 갱신도 안 되면 그때만 로그아웃하고 null.
     */
    private fun fetchWithAuth(ctx: Context, store: Store): GitHubClient.Viewer? {
        val token = when (val t = TokenManager.token(ctx, store)) {
            is TokenManager.Result.Ok -> t.token
            is TokenManager.Result.NeedLogin -> return signOut(ctx, store, t.reason)
        }
        return try {
            fetchRetryingNetwork(ctx, token)
        } catch (e: GitHubClient.FetchException) {
            if (!e.unauthorized) throw e
            DiagLog.log(ctx, "GitHub 가 401 응답 → 토큰 갱신 후 다시 시도")
            when (val t = TokenManager.token(ctx, store, rejected = token)) {
                is TokenManager.Result.NeedLogin -> signOut(ctx, store, t.reason)
                is TokenManager.Result.Ok -> try {
                    fetchRetryingNetwork(ctx, t.token)
                } catch (e2: GitHubClient.FetchException) {
                    if (e2.unauthorized) signOut(ctx, store, ctx.getString(R.string.error_refreshed_token_rejected)) else throw e2
                }
            }
        }
    }

    /** 인터넷 연결 오류(DNS 실패 등)면 3초 기다렸다가 한 번 더 */
    private fun fetchRetryingNetwork(ctx: Context, token: String): GitHubClient.Viewer = try {
        GitHubClient.fetchViewer(token)
    } catch (e: IOException) {
        if (e is GitHubClient.FetchException || !isNetworkError(e)) throw e
        DiagLog.log(ctx, "네트워크 오류 ${e.javaClass.simpleName} → 3초 뒤 한 번 더 (네트워크: ${DiagLog.network(ctx)})")
        Thread.sleep(3_000)
        GitHubClient.fetchViewer(token)
    }

    private fun signOut(ctx: Context, store: Store, reason: String): Nothing? {
        DiagLog.log(ctx, "자동 로그아웃: $reason")
        store.signOut(notice = reason)
        Reminders.cancel(ctx)
        WidgetUpdater.updateAll(ctx)
        return null
    }

    private fun failure(
        ctx: Context, store: Store, noCache: Boolean, e: Exception, retryable: Boolean, willRetry: Boolean,
    ): Outcome {
        val message = friendlyError(ctx, e)
        DiagLog.log(
            ctx,
            "동기화 실패: $message [${e.javaClass.simpleName}: ${e.message}] → ${if (retryable && willRetry) "잠시 뒤 재시도" else "재시도 안 함"}",
        )
        store.lastError = message
        // 캐시가 있으면 위젯은 이전 그래프를 그대로 보여주므로 다시 그릴 필요 없음
        if (noCache) WidgetUpdater.updateAll(ctx)
        return Outcome.Failed(retryable)
    }
}
