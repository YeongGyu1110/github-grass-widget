package com.ghwidget.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.Store
import com.ghwidget.app.work.Refresh
import com.ghwidget.app.work.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** 알림 시각에 실행: GitHub 에서 최신 데이터를 받아 오늘 기여가 0건이면 알리고, 다음 알람을 건다 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val store = Store(ctx)
        val settings = store.settings()
        if (!settings.reminderEnabled || store.accessToken == null) {
            Reminders.cancel(ctx)
            return@withContext Result.success()
        }
        val now = LocalDateTime.now()
        if (store.reminderMutedDate == now.toLocalDate().toString()) {
            DiagLog.log(ctx, "잔디 알림 확인: 오늘은 그만 → 건너뜀")
        } else if (!Reminders.isDue(now, settings)) {
            // 작업이 밀려 자정을 넘겼거나 시계가 바뀐 경우: 새 날짜 기준으로는 아직 알릴 때가 아니다
            DiagLog.log(ctx, "잔디 알림 확인: 알림 시간대가 아님 (${Reminders.describe(ctx, now)}) → 건너뜀")
        } else {
            // 방금 커밋했을 수도 있으니 먼저 최신 데이터를 받는다. 실패하면 저장된 데이터로 판단
            Sync.run(ctx, "잔디 알림 확인")
            val data = store.cachedData()
            if (store.accessToken == null) {
                // 동기화 중 로그아웃됨 (Sync 가 알람도 취소함)
                return@withContext Result.success()
            }
            val count = data?.let { Reminders.todayCount(it, now.toLocalDate()) } ?: 0
            if (count > 0) {
                DiagLog.log(ctx, "잔디 알림 확인: 오늘 기여 ${count}건 → 알림 안 함")
                Reminders.clearNotification(ctx)
            } else {
                val (index, total) = Reminders.position(now, settings)
                DiagLog.log(ctx, "잔디 알림 확인: 오늘 기여 0건 → 알림 ($index/$total)")
                Reminders.notify(ctx, index, total, streak = data?.currentStreak() ?: 0, now = now)
            }
        }
        Reminders.schedule(ctx)
        Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = Refresh.foregroundInfo(applicationContext)
}
