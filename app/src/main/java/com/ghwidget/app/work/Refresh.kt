package com.ghwidget.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.GitHubClient
import com.ghwidget.app.data.Http
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.TokenManager
import com.ghwidget.app.data.friendlyError
import com.ghwidget.app.data.isNetworkError
import com.ghwidget.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/** contributions 를 받아와 캐시에 저장하고, 그래프가 바뀌었을 때만 위젯을 다시 그린다 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { work() }

    /** Android 11 이하에서 급한(expedited) 작업이 포그라운드 서비스로 실행될 때만 쓰인다 */
    override suspend fun getForegroundInfo(): ForegroundInfo = Refresh.foregroundInfo(applicationContext)

    private fun work(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        if (store.accessToken == null) {
            WidgetUpdater.updateAll(ctx)
            return Result.success()
        }
        val reason = inputData.getString(Refresh.KEY_REASON) ?: "주기 갱신"
        DiagLog.log(ctx, "동기화 시작: $reason (시도 ${runAttemptCount + 1}, 네트워크: ${DiagLog.network(ctx)})")

        store.lastAttemptAt = System.currentTimeMillis()
        val cached = store.cachedData()
        return try {
            val viewer = fetchWithAuth(ctx, store) ?: return Result.success()
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
            DiagLog.log(ctx, "동기화 성공 (${if (changed) "그래프 바뀜 → 위젯 다시 그림" else "변화 없음"})")
            Result.success()
        } catch (e: GitHubClient.FetchException) {
            failure(store, cached, e, retryable = e.retryable)
        } catch (e: Exception) {
            failure(store, cached, e, retryable = isNetworkError(e))
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
                    if (e2.unauthorized) signOut(ctx, store, "갱신한 토큰도 거부되었습니다 (HTTP 401)") else throw e2
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
        WidgetUpdater.updateAll(ctx)
        return null
    }

    private fun failure(store: Store, cached: ContributionData?, e: Exception, retryable: Boolean): Result {
        val message = friendlyError(e)
        val retry = retryable && runAttemptCount < 2
        DiagLog.log(
            applicationContext,
            "동기화 실패: $message [${e.javaClass.simpleName}: ${e.message}] → ${if (retry) "잠시 뒤 재시도" else "재시도 안 함"}",
        )
        store.lastError = message
        // 캐시가 있으면 위젯은 이전 그래프를 그대로 보여주므로 다시 그릴 필요 없음
        if (cached == null) WidgetUpdater.updateAll(applicationContext)
        return if (retry) Result.retry() else Result.success()
    }
}

object Refresh {
    const val NOW = "refresh_now"
    const val KEY_REASON = "reason"
    private const val PERIODIC = "refresh_periodic"
    private const val CHANNEL = "sync"

    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * 지금 바로 동기화. reason 은 진단 기록에 남는다.
     * 급한(expedited) 작업으로 요청해서, 위젯을 눌렀을 때처럼 앱이 백그라운드여도 네트워크를 쓸 수 있게 한다.
     */
    fun now(context: Context, reason: String) {
        DiagLog.log(context, "새로고침 요청: $reason")
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_REASON to reason))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    /**
     * 주기 갱신 등록. UPDATE 정책이라 이미 등록돼 있으면 다음 실행 시각은 유지한 채
     * 간격·조건만 바꾼다 (앱 업데이트로 조건이 바뀌어도 반영됨)
     */
    fun ensurePeriodic(context: Context) {
        val hours = Store(context).settings().refreshHours.coerceAtLeast(1).toLong()
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(hours, TimeUnit.HOURS)
            // 자동 갱신은 배터리가 부족할 때 건너뛴다 (그래프를 직접 누르는 새로고침은 항상 실행)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(NOW)
        }
    }

    /** 급한 작업이 포그라운드 서비스로 실행될 때(Android 11 이하) 잠깐 보이는 알림 */
    fun foregroundInfo(context: Context): ForegroundInfo {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "동기화", NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("GitHub 잔디 동기화 중")
            .setOngoing(true)
            .build()
        return ForegroundInfo(1001, notification)
    }
}
