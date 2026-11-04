package com.ghwidget.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.ghwidget.app.R
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
        val outcome = Sync.run(applicationContext, reason, runAttemptCount, willRetry = canRetry)
        // 같은 요청이 새로 들어와 이 작업이 교체됐으면 결과는 새 작업이 알린다 (Toast 가 두 번 뜨지 않게)
        if (inputData.getBoolean(Refresh.KEY_SHOW_RESULT, false) && !isStopped) {
            Refresh.showResult(applicationContext, outcome)
        }
        when (outcome) {
            is Sync.Outcome.Failed -> if (outcome.retryable && canRetry) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    /** Android 11 이하에서 급한(expedited) 작업이 포그라운드 서비스로 실행될 때만 쓰인다 */
    override suspend fun getForegroundInfo(): ForegroundInfo = Refresh.foregroundInfo(applicationContext)
}

object Refresh {
    const val NOW = "refresh_now"
    const val KEY_REASON = "reason"
    const val KEY_SHOW_RESULT = "show_result"
    private const val PERIODIC = "refresh_periodic"
    private const val CHANNEL = "sync"

    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * 지금 바로 동기화. reason 은 진단 기록에 남는다.
     * 급한(expedited) 작업으로 요청해서, 위젯을 눌렀을 때처럼 앱이 백그라운드여도 네트워크를 쓸 수 있게 한다.
     * showResult: 끝나면 결과를 Toast 로 알린다 (위젯 그래프를 눌렀을 때. 재시도하면 시도마다 알림)
     */
    fun now(context: Context, reason: String, showResult: Boolean = false) {
        DiagLog.log(context, "새로고침 요청: $reason")
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_REASON to reason, KEY_SHOW_RESULT to showResult))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    /** 동기화 결과 Toast. 작업 스레드에서 불려도 되도록 메인 스레드로 넘긴다 */
    fun showResult(context: Context, outcome: Sync.Outcome) {
        val (text, length) = when (outcome) {
            is Sync.Outcome.Success -> context.getString(
                if (outcome.changed) R.string.widget_refreshed_toast else R.string.widget_up_to_date_toast,
            ) to Toast.LENGTH_SHORT
            is Sync.Outcome.Failed -> context.getString(
                R.string.widget_refresh_failed_toast,
                Store(context).lastError ?: context.getString(R.string.error_unknown),
            ) to Toast.LENGTH_LONG
            Sync.Outcome.SignedOut -> context.getString(R.string.widget_sign_in_again) to Toast.LENGTH_LONG
            Sync.Outcome.NotSignedIn -> context.getString(R.string.widget_sign_in) to Toast.LENGTH_LONG
        }
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, length).show() }
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
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.sync_channel), NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.sync_notification_title))
            .setOngoing(true)
            .build()
        return ForegroundInfo(1001, notification)
    }
}
