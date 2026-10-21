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
import com.ghwidget.app.data.ContributionData
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
        val token = store.accessToken
        if (token == null) {
            WidgetUpdater.updateAll(applicationContext)
            return Result.success()
        }

        store.lastAttemptAt = System.currentTimeMillis()
        val cached = store.cachedData()
        return try {
            val data = GitHubClient.fetchViewer(token)
            store.saveData(data)
            // 그래프 내용이 같으면 다시 그리지 않는다 (런처로 비트맵을 보내는 비용 절약)
            if (cached == null || cached.days != data.days || cached.total != data.total) {
                WidgetUpdater.updateAll(applicationContext)
            }
            Result.success()
        } catch (e: GitHubClient.FetchException) {
            if (e.unauthorized) {
                // 토큰이 취소·만료됨: 로그아웃 처리하고 위젯에 다시 로그인하라고 표시
                store.signOut()
                store.lastError = e.message
                WidgetUpdater.updateAll(applicationContext)
                Result.success()
            } else {
                failure(store, cached, e, retryable = e.retryable)
            }
        } catch (e: Exception) {
            // 네트워크 연결 문제 등
            failure(store, cached, e, retryable = true)
        }
    }

    private fun failure(store: Store, cached: ContributionData?, e: Exception, retryable: Boolean): Result {
        store.lastError = e.message ?: e.javaClass.simpleName
        // 캐시가 있으면 위젯은 이전 그래프를 그대로 보여주므로 다시 그릴 필요 없음
        if (cached == null) WidgetUpdater.updateAll(applicationContext)
        return if (retryable && runAttemptCount < 2) Result.retry() else Result.success()
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
