package com.ghwidget.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.Store
import java.time.LocalDate

/**
 * - 알람: 잔디 알림 확인 작업(ReminderWorker)을 급한 작업으로 실행
 * - "오늘은 그만": 오늘 남은 알림을 멈춤
 * - 재부팅·앱 업데이트·시간 변경: 알람이 사라지거나 어긋나므로 다시 예약
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_REMIND -> {
                val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                    // 알람 직후 잠깐 주어지는 실행 허용 시간 안에, 백그라운드에서도 네트워크를 쓸 수 있게 급한 작업으로
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork("reminder_check", ExistingWorkPolicy.REPLACE, req)
            }
            Reminders.ACTION_MUTE_TODAY -> {
                Store(context).reminderMutedDate = LocalDate.now().toString()
                Reminders.clearNotification(context)
                DiagLog.log(context, "잔디 알림: '오늘은 그만' 누름")
                Reminders.schedule(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Reminders.schedule(context)
        }
    }
}
