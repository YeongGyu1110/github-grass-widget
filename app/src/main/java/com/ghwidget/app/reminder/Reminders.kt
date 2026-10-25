package com.ghwidget.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ghwidget.app.MainActivity
import com.ghwidget.app.R
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.Store
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 잔디 알림: 늦은 시간까지 오늘 GitHub 기여가 하나도 없으면 여러 번 나눠 알린다.
 *
 * - 시작 시각부터 간격마다, 마지막은 23:30 (자정 30분 전)
 * - 알람이 울리면 ReminderWorker 가 GitHub 에서 최신 데이터를 받아 오늘 기여가 0건일 때만 알림
 * - 커밋은 커밋 자체의 시간대 기준으로 집계되므로 "오늘"은 이 기기의 날짜, 마감은 자정
 * - 알람은 한 번에 하나만 걸어 두고, 울릴 때마다 다음 시각을 다시 건다
 */
object Reminders {
    const val ACTION_REMIND = "com.ghwidget.app.action.REMIND"
    const val ACTION_MUTE_TODAY = "com.ghwidget.app.action.MUTE_TODAY"
    private const val CHANNEL = "contribution_reminder"
    private const val NOTIFICATION_ID = 2001

    // ------------------------------------------------------------ 시각 계산 (순수 함수, 테스트 대상)

    /** date 의 알림 시각들: 시작 시각부터 간격마다, 23:30 이 마지막 */
    fun slots(date: LocalDate, startHour: Int, intervalMin: Int): List<LocalDateTime> {
        val last = date.atTime(23, 30)
        val out = ArrayList<LocalDateTime>()
        var t = date.atTime(startHour.coerceIn(0, 23), 0)
        while (t.isBefore(last)) {
            out += t
            t = t.plusMinutes(intervalMin.coerceAtLeast(15).toLong())
        }
        out += last
        return out
    }

    fun slots(date: LocalDate, s: Settings) = slots(date, s.reminderStartHour, s.reminderIntervalMin)

    /** now 이후 첫 알림 시각. skipToday 면 내일 첫 시각 */
    fun nextSlot(now: LocalDateTime, s: Settings, skipToday: Boolean): LocalDateTime {
        if (!skipToday) slots(now.toLocalDate(), s).firstOrNull { it.isAfter(now) }?.let { return it }
        return slots(now.toLocalDate().plusDays(1), s).first()
    }

    /** 지금이 오늘 몇 번째 알림인지 (1부터)와 전체 개수. 알람이 조금 늦거나 빨라도 맞도록 2분 여유 */
    fun position(now: LocalDateTime, s: Settings): Pair<Int, Int> {
        val all = slots(now.toLocalDate(), s)
        val index = all.indexOfLast { !it.isAfter(now.plusMinutes(2)) } + 1
        return index.coerceIn(1, all.size) to all.size
    }

    /** 지금이 오늘 알림 시간대인지 (첫 시각 2분 전 ~ 자정). 확인 작업이 자정을 넘겨 늦게 실행된 경우 등을 거른다 */
    fun isDue(now: LocalDateTime, s: Settings): Boolean =
        !now.plusMinutes(2).isBefore(slots(now.toLocalDate(), s).first())

    fun todayCount(data: ContributionData, today: LocalDate = LocalDate.now()): Int = data.byDate[today]?.count ?: 0

    /** 알림 문구 (제목 to 본문): 뒤로 갈수록 강하게 */
    fun message(context: Context, index: Int, total: Int, streak: Int, minutesLeft: Long): Pair<String, String> {
        val r = context.resources
        val left = if (minutesLeft >= 60) r.getString(R.string.left_hm, (minutesLeft / 60).toInt(), (minutesLeft % 60).toInt())
        else r.getString(R.string.left_m, minutesLeft.toInt())
        val atRisk = if (streak > 0) r.getString(R.string.reminder_at_risk_streak, streak) else r.getString(R.string.reminder_at_risk_empty)
        return when {
            index >= total -> r.getString(R.string.reminder_last_title, left) to r.getString(R.string.reminder_last_body, atRisk)
            minutesLeft <= 90 -> r.getString(R.string.reminder_soon_title) to r.getString(R.string.reminder_soon_body, left, atRisk)
            index == 1 -> r.getString(R.string.reminder_first_title) to
                if (streak > 0) r.getString(R.string.reminder_first_body_streak, streak) else r.getString(R.string.reminder_first_body)
            else -> r.getString(R.string.reminder_mid_title, index, total) to
                if (streak > 0) r.getString(R.string.reminder_mid_body_streak, left, streak) else r.getString(R.string.reminder_mid_body, left)
        }
    }

    // ------------------------------------------------------------ 알람 예약

    /** 설정·로그인 상태에 맞춰 다음 알람을 건다 (켜져 있지 않으면 취소). 여러 번 불러도 안전 */
    fun schedule(context: Context) {
        val ctx = context.applicationContext
        val store = Store(ctx)
        val s = store.settings()
        if (!s.reminderEnabled || store.accessToken == null) {
            cancel(ctx)
            return
        }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val cached = store.cachedData()
        // 오늘 이미 기여했거나 "오늘은 그만"을 눌렀으면 내일 첫 시각부터
        val skipToday = store.reminderMutedDate == today.toString() ||
            (cached != null && todayCount(cached, today) > 0 && isToday(cached.fetchedAt, today))
        val next = nextSlot(now, s, skipToday)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        if (store.reminderNextAt != at) {
            store.reminderNextAt = at
            DiagLog.log(ctx, "잔디 알림 예약: ${describe(ctx, next, today)}${if (skipToday) " (오늘은 건너뜀)" else ""}")
        }
    }

    fun cancel(context: Context) {
        val ctx = context.applicationContext
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
        val store = Store(ctx)
        if (store.reminderNextAt != 0L) {
            store.reminderNextAt = 0L
            DiagLog.log(ctx, "잔디 알림 예약 취소")
        }
    }

    /** 언어에 맞는 시각 형식 (한국어 21:00, 영어 9:00 PM) */
    fun timeFormat(context: Context): DateTimeFormatter = DateTimeFormatter.ofPattern(context.getString(R.string.time_pattern))

    fun describe(context: Context, t: LocalDateTime, today: LocalDate = LocalDate.now()): String = context.getString(
        if (t.toLocalDate() == today) R.string.reminder_today_at else R.string.reminder_tomorrow_at,
        t.format(timeFormat(context)),
    )

    private fun isToday(epochMs: Long, today: LocalDate) =
        java.time.Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate() == today

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 10,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ------------------------------------------------------------ 알림

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** 오늘 기여가 없다는 알림. 같은 자리에 덮어써서 알림창에 쌓이지 않게 하되, 매번 소리·진동으로 알린다 */
    fun notify(context: Context, index: Int, total: Int, streak: Int, now: LocalDateTime = LocalDateTime.now()) {
        val ctx = context.applicationContext
        if (!canNotify(ctx)) {
            DiagLog.log(ctx, "잔디 알림을 보내지 못함: 알림 권한이 꺼져 있음")
            return
        }
        val minutesLeft = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMinutes().coerceAtLeast(1)
        val (title, text) = message(ctx, index, total, streak, minutesLeft)
        post(ctx, title, text)
    }

    /** 설정에서 "알림 미리 보기" */
    fun preview(context: Context) {
        val ctx = context.applicationContext
        val (title, text) = message(ctx, 1, 4, 12, 180)
        post(ctx, title, ctx.getString(R.string.reminder_preview_suffix, text))
    }

    private fun post(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.reminder_channel_desc)
            },
        )
        val open = PendingIntent.getActivity(
            ctx, 11,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val mute = PendingIntent.getBroadcast(
            ctx, 12,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_MUTE_TODAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF26A641.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(Notification.Action.Builder(null, ctx.getString(R.string.reminder_action_stop), mute).build())
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    fun clearNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
