package com.ghwidget.app

import android.app.AlarmManager
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.ContributionDay
import com.ghwidget.app.data.GitHubAuth
import com.ghwidget.app.data.Settings
import com.ghwidget.app.data.Store
import com.ghwidget.app.reminder.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderTest {

    private val day = LocalDate.of(2026, 10, 23)
    private fun at(h: Int, m: Int) = day.atTime(h, m)

    @Test
    fun slotsFromStartUntil2330() {
        assertEquals(
            listOf(at(21, 0), at(22, 0), at(23, 0), at(23, 30)),
            Reminders.slots(day, 21, 60),
        )
        assertEquals(
            listOf(at(22, 0), at(22, 30), at(23, 0), at(23, 30)),
            Reminders.slots(day, 22, 30),
        )
        assertEquals(8, Reminders.slots(day, 20, 30).size) // 20:00, 20:30, … 23:00, 23:30
    }

    @Test
    fun nextSlotSkipsPastTimesAndToday() {
        val s = Settings(reminderEnabled = true, reminderStartHour = 21, reminderIntervalMin = 60)
        assertEquals(at(21, 0), Reminders.nextSlot(at(9, 0), s, skipToday = false))
        assertEquals(at(23, 0), Reminders.nextSlot(at(22, 10), s, skipToday = false))
        // 23:30 이 지났으면 내일 첫 시각
        assertEquals(day.plusDays(1).atTime(21, 0), Reminders.nextSlot(at(23, 40), s, skipToday = false))
        // 오늘 이미 기여했으면 내일 첫 시각
        assertEquals(day.plusDays(1).atTime(21, 0), Reminders.nextSlot(at(9, 0), s, skipToday = true))
    }

    @Test
    @Config(qualifiers = "ko")
    fun positionAndEscalatingMessages() {
        val s = Settings(reminderStartHour = 21, reminderIntervalMin = 60)
        assertEquals(1 to 4, Reminders.position(at(21, 0), s))
        assertEquals(3 to 4, Reminders.position(at(22, 59), s)) // 알람이 1분 일찍 울려도 23:00 회차로 본다
        assertEquals(4 to 4, Reminders.position(at(23, 30), s))

        val ctx = RuntimeEnvironment.getApplication()
        val first = Reminders.message(ctx, 1, 4, streak = 12, minutesLeft = 180)
        assertTrue(first.first.contains("비어 있어요"))
        assertTrue(first.second.contains("연속 12일"))
        assertTrue(Reminders.message(ctx, 3, 4, 12, 60).first.contains("직전")) // 90분 이하
        val last = Reminders.message(ctx, 4, 4, 12, 30)
        assertEquals("마지막 알림: 자정까지 30분 🚨", last.first)
        assertTrue(last.second.contains("연속 12일 기록이 끊겨요"))
        assertTrue(Reminders.message(ctx, 4, 4, 0, 30).second.contains("빈칸"))
        assertEquals("아직 오늘 기여가 없어요 (2/4)", Reminders.message(ctx, 2, 4, 12, 150).first)
        assertTrue(Reminders.message(ctx, 2, 4, 12, 150).second.startsWith("자정까지 2시간 30분 남았어요."))
    }

    @Test
    @Config(qualifiers = "en")
    fun englishMessages() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("No contributions yet today 🌱", Reminders.message(ctx, 1, 4, 12, 180).first)
        assertEquals("Last reminder: 30m until midnight 🚨", Reminders.message(ctx, 4, 4, 12, 30).first)
        assertTrue(Reminders.message(ctx, 4, 4, 12, 30).second.contains("your 12-day streak ends"))
        assertTrue(Reminders.message(ctx, 2, 4, 0, 150).second.startsWith("2h 30m left until midnight."))
        assertEquals("Tomorrow 9:00 PM", Reminders.describe(ctx, at(21, 0), day.minusDays(1)))
    }

    @Test
    fun isDueOnlyWithinTodaysWindow() {
        val s = Settings(reminderStartHour = 21, reminderIntervalMin = 60)
        assertTrue(Reminders.isDue(at(20, 59), s)) // 알람이 1분 일찍 울려도
        assertTrue(Reminders.isDue(at(23, 30), s))
        assertFalse(Reminders.isDue(at(0, 20), s)) // 23:30 확인 작업이 자정을 넘겨 실행된 경우
        assertFalse(Reminders.isDue(at(15, 0), s))
    }

    @Test
    fun todayCountUsesDeviceDate() {
        val data = ContributionData(
            "me",
            listOf(ContributionDay(day.minusDays(1), 4, 2), ContributionDay(day, 0, 0)),
            4, 0L,
        )
        assertEquals(0, Reminders.todayCount(data, day))
        assertEquals(4, Reminders.todayCount(data, day.minusDays(1)))
        assertEquals(0, Reminders.todayCount(data, day.plusDays(1))) // 아직 달력에 없는 날
    }

    @Test
    fun schedulesExactAlarmOnlyWhenEnabledAndSignedIn() {
        val ctx = RuntimeEnvironment.getApplication()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val store = Store(ctx)

        // 꺼져 있으면 알람 없음
        Reminders.schedule(ctx)
        assertNull(shadowOf(am).nextScheduledAlarm)

        store.signIn(GitHubAuth.TokenSet("t", null, null, null))
        store.save(Settings(reminderEnabled = true, reminderStartHour = 21, reminderIntervalMin = 60))
        Reminders.schedule(ctx)
        val alarm = shadowOf(am).nextScheduledAlarm
        assertTrue("알람이 예약되지 않음", alarm != null)
        val time = Instant.ofEpochMilli(alarm!!.triggerAtTime).atZone(ZoneId.systemDefault()).toLocalTime()
        assertTrue("예약 시각 $time", time in listOf(LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(23, 0), LocalTime.of(23, 30)))

        // 로그아웃하면 알람도 취소
        store.signOut()
        Reminders.schedule(ctx)
        assertNull(shadowOf(am).nextScheduledAlarm)
    }
}
