package com.ghwidget.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ghwidget.app.data.GitHubClient
import com.ghwidget.app.data.Store
import com.ghwidget.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** contributions 를 받아와 캐시에 저장하고, 그래프가 바뀌었을 때만 위젯을 다시 그린다 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { work() }

    private fun work(): Result {
        val store = Store(applicationContext)
        val s = store.settings()
        if (s.username.isBlank()) {
            WidgetUpdater.updateAll(applicationContext)
            return Result.success()
        }

        store.lastAttemptAt = System.currentTimeMillis()
        val cached = store.cachedData()
        return try {
            val etag = if (cached != null) store.etagFor(s.username) else null
            when (val r = GitHubClient.fetch(s.username, s.token, etag)) {
                // 바뀐 것이 없으면 받은 시각만 갱신하고 위젯은 그대로 둔다
                GitHubClient.Result.NotModified ->
                    store.saveData(cached!!.copy(fetchedAt = System.currentTimeMillis()), etag)
                is GitHubClient.Result.Fetched -> {
                    store.saveData(r.data, r.etag)
                    // 그래프 내용이 같으면 다시 그리지 않는다 (런처로 비트맵을 보내는 비용 절약)
                    if (cached == null || cached.days != r.data.days || cached.total != r.data.total) {
                        WidgetUpdater.updateAll(applicationContext)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            store.lastError = e.message ?: e.javaClass.simpleName
            // 캐시가 있으면 위젯은 이전 그래프를 그대로 보여주므로 다시 그릴 필요 없음
            if (cached == null) WidgetUpdater.updateAll(applicationContext)
            // 네트워크 문제·요청 제한·서버 오류만 재시도 (잘못된 사용자 이름 등은 재시도해도 같음)
            val retryable = e !is GitHubClient.FetchException || e.retryable
            if (retryable && runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}

object Refresh {
    const val NOW = "refresh_now"
    private const val PERIODIC = "refresh_periodic"

    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun now(context: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
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
}
