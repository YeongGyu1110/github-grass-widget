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

/** contributions 를 받아와 캐시에 저장하고 모든 위젯을 다시 그린다 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = Store(applicationContext)
        val s = store.settings()
        if (s.username.isBlank()) {
            WidgetUpdater.updateAll(applicationContext)
            return@withContext Result.success()
        }

        store.lastAttemptAt = System.currentTimeMillis()
        val result = try {
            store.saveData(GitHubClient.fetch(s.username, s.token))
            Result.success()
        } catch (e: Exception) {
            store.lastError = e.message ?: e.javaClass.simpleName
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        WidgetUpdater.updateAll(applicationContext)
        result
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

    /** 주기 갱신 등록. replace=true 면 간격이 바뀐 경우 갱신 */
    fun ensurePeriodic(context: Context, replace: Boolean = false) {
        val hours = Store(context).settings().refreshHours.coerceAtLeast(1).toLong()
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(hours, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
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
