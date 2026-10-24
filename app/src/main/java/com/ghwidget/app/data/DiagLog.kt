package com.ghwidget.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 진단 기록: 동기화·로그인·토큰 갱신·네트워크 오류 같은 사건을 파일에 남긴다.
 * 설정 → 진단 기록에서 볼 수 있고, logcat 에도 "GhWidget" 태그로 같이 찍힌다.
 * 토큰 값 같은 비밀 정보는 절대 남기지 않는다.
 */
object DiagLog {
    private const val FILE = "diagnostics.log"
    private const val KEEP_LINES = 300
    private val lock = Any()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun log(context: Context, message: String) {
        Log.i("GhWidget", message)
        val line = "${fmt.format(Date())}  $message"
        synchronized(lock) {
            runCatching {
                val f = file(context)
                f.appendText(line + "\n")
                // 너무 길어지면 최근 것만 남긴다
                if (f.length() > 64_000) {
                    val lines = f.readLines()
                    if (lines.size > KEEP_LINES) f.writeText(lines.takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    /** 기록 종류 (진단 기록 화면에서 골라 보기용) */
    enum class Kind(val label: String) { SYNC("동기화"), AUTH("로그인·토큰"), REMINDER("잔디 알림"), APP("앱") }

    data class Entry(val time: String, val message: String) {
        val kind: Kind get() = kindOf(message)
        val isError: Boolean get() = isError(message)
        /** 파일에 저장된 한 줄 그대로 (복사용) */
        val line: String get() = if (time.isEmpty()) message else "$time  $message"
    }

    /**
     * 종류는 따로 저장하지 않고 문구로 나눈다. 그래서 이전에 남은 기록도 그대로 분류된다.
     * 새 문구를 추가할 때는 아래 규칙에 맞는지 확인할 것 (DiagLogTest).
     */
    fun kindOf(message: String): Kind = when {
        message.startsWith("잔디 알림") -> Kind.REMINDER
        message.startsWith("동기화") || message.startsWith("새로고침") || message.startsWith("네트워크") -> Kind.SYNC
        "토큰" in message || "로그인" in message || "로그아웃" in message || "401" in message -> Kind.AUTH
        else -> Kind.APP
    }

    /** "동기화 실패: …"처럼 실패를 알리는 기록 ("(지난 실패)" 때문에 다시 받는 기록은 제외) */
    fun isError(message: String): Boolean = "실패:" in message || "못함" in message || "401" in message ||
        message.startsWith("자동 로그아웃") || message.startsWith("네트워크 오류")

    /** "MM-dd HH:mm:ss  내용" 한 줄을 나눈다 */
    fun parse(line: String): Entry {
        val sep = line.indexOf("  ")
        return if (sep == 14) Entry(line.substring(0, sep), line.substring(sep + 2)) else Entry("", line)
    }

    /** 최근 것이 먼저 오도록 */
    fun read(context: Context): List<Entry> = synchronized(lock) {
        runCatching { file(context).readLines().filter { it.isNotBlank() }.reversed().map(::parse) }.getOrDefault(emptyList())
    }

    fun clear(context: Context) = synchronized(lock) { file(context).delete(); Unit }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    /** 지금 이 앱이 쓸 수 있는 네트워크 상태 요약 */
    fun network(context: Context): String = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        // 앱이 백그라운드에서 네트워크가 막혀 있으면 activeNetwork 가 null 이다
        val n = cm.activeNetwork ?: return "네트워크 없음/차단됨"
        val caps = cm.getNetworkCapabilities(n) ?: return "네트워크 정보 없음"
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "모바일 데이터"
            else -> "기타"
        }
        val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        "$type${if (ok) "" else " (인터넷 확인 안 됨)"}"
    }.getOrDefault("확인 실패")
}
