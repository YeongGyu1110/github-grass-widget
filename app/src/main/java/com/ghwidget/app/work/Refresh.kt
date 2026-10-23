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
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** 주기 갱신·수동 새로고침 작업. 실제 동기화는 Sync.run */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val reason = inputData.getString(Refresh.KEY_REASON) ?: "주기 갱신"
        val canRetry = runAttemptCount < 2
        when (val o = Sync.run(applicationContext, reason, runAttemptCount, willRetry = canRetry)) {
            is Sync.Outcome.Failed -> if (o.retryable && canRetry) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    /** Android 11 이하에서 급한(expedited) 작업이 포그라운드 서비스로 실행될 때만 쓰인다 */
    override suspend fun getForegroundInfo(): ForegroundInfo = Refresh.foregroundInfo(applicationContext)
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
