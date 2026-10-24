package com.ghwidget.app

import com.ghwidget.app.data.DiagLog
import com.ghwidget.app.data.DiagLog.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 진단 기록 종류 나누기: 앱이 실제로 남기는 문구가 모두 맞는 종류로 들어가는지 */
class DiagLogTest {

    private fun assertKind(kind: Kind, vararg messages: String) =
        messages.forEach { assertEquals(it, kind, DiagLog.kindOf(it)) }

    @Test
    fun classifiesEveryMessageTheAppWrites() {
        assertKind(
            Kind.SYNC,
            "동기화 시작: 주기 갱신 (시도 1, 네트워크: Wi-Fi)",
            "동기화 성공 (변화 없음)",
            "동기화 실패: GitHub가 로그인 토큰을 거부했습니다 [FetchException: 401] → 재시도 안 함",
            "새로고침 요청: 앱 열기 (데이터 오래됨/지난 실패)",
            "네트워크 오류 UnknownHostException → 3초 뒤 한 번 더 (네트워크: Wi-Fi)",
        )
        assertKind(
            Kind.AUTH,
            "토큰 갱신 시도 (만료됨: 10-24 09:01)",
            "토큰 갱신 성공 → 다음 만료 10-24 18:48",
            "토큰 갱신 실패: 로그인 갱신 기한이 지났습니다",
            "GitHub 가 401 응답 → 토큰 갱신 후 다시 시도",
            "로그인 완료 (토큰 만료: 10-23 16:37, 갱신 토큰: 있음)",
            "자동 로그아웃: 로그인 갱신 기한이 지났습니다",
            "사용자가 직접 로그아웃",
        )
        assertKind(
            Kind.REMINDER,
            "잔디 알림 켬",
            "잔디 알림 예약: 내일 20:00 (오늘은 건너뜀)",
            "잔디 알림 예약 취소",
            "잔디 알림 확인: 오늘 기여 0건 → 알림 (1/4)",
            "잔디 알림: '오늘은 그만' 누름",
            "잔디 알림을 보내지 못함: 알림 권한이 꺼져 있음",
        )
        assertKind(Kind.APP, "앱 화면 열림", "앱 화면 다시 만들어짐 (테마 등 설정 변경)", "앱 테마 변경: LIGHT → SYSTEM")
    }

    @Test
    fun marksOnlyRealProblemsAsErrors() {
        listOf(
            "동기화 실패: GitHub 서버 오류 (HTTP 502) [FetchException: 502] → 잠시 뒤 재시도",
            "토큰 갱신 실패: 로그인 갱신 기한이 지났습니다",
            "자동 로그아웃: 갱신한 토큰도 거부되었습니다 (HTTP 401)",
            "네트워크 오류 UnknownHostException → 3초 뒤 한 번 더 (네트워크: Wi-Fi)",
            "잔디 알림을 보내지 못함: 알림 권한이 꺼져 있음",
        ).forEach { assertTrue(it, DiagLog.isError(it)) }
        listOf(
            "동기화 성공 (변화 없음)",
            "새로고침 요청: 앱 열기 (데이터 오래됨/지난 실패)", // 지난 실패 때문에 다시 받는 것뿐
            "사용자가 직접 로그아웃",
            "토큰 갱신 시도 (만료됨: 10-24 09:01)",
        ).forEach { assertFalse(it, DiagLog.isError(it)) }
    }

    @Test
    fun parsesStoredLines() {
        val e = DiagLog.parse("10-24 10:48:38  토큰 갱신 시도 (만료됨: 10-24 09:01)")
        assertEquals("10-24 10:48:38", e.time)
        assertEquals("토큰 갱신 시도 (만료됨: 10-24 09:01)", e.message)
        assertEquals(Kind.AUTH, e.kind)
        // 복사하면 저장된 줄 그대로
        assertEquals("10-24 10:48:38  토큰 갱신 시도 (만료됨: 10-24 09:01)", e.line)
        // 형식이 다른 줄도 버리지 않는다
        assertEquals("이상한 줄", DiagLog.parse("이상한 줄").message)
    }
}
