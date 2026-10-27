package com.ghwidget.app

import com.ghwidget.app.work.Sync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** 동기화는 한 번에 하나만: 겹치면 나중 것은 새로 요청하지 않고 먼저 끝난 결과를 쓴다 */
class SyncTest {

    @Test
    fun overlappingSyncReusesTheRunningOne() {
        val started = CountDownLatch(1)
        val first = thread {
            Sync.exclusive(onReuse = {}) {
                started.countDown()
                Thread.sleep(300) // 네트워크 요청 중
                Sync.Outcome.Success(changed = true)
            }
        }
        started.await()

        var secondRequested = false
        var reused = false
        val second = Sync.exclusive(onReuse = { reused = true }) {
            secondRequested = true
            Sync.Outcome.Success(changed = false)
        }
        first.join()

        assertFalse("겹친 동기화가 또 요청함", secondRequested)
        assertTrue(reused)
        assertEquals(Sync.Outcome.Success(changed = true), second)
    }

    @Test
    fun laterSyncStillRuns() {
        val finishedBefore = Sync.finished.value
        Sync.exclusive(onReuse = {}) { Sync.Outcome.Success(changed = false) }
        // 앞의 동기화가 이미 끝난 뒤에 시작하면 새로 실행한다
        var ran = false
        Sync.exclusive(onReuse = { error("다시 쓰면 안 됨") }) {
            ran = true
            Sync.Outcome.Failed(retryable = true)
        }
        assertTrue(ran)
        assertEquals(finishedBefore + 2, Sync.finished.value)
    }
}
