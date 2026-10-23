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

    /** 최근 것이 먼저 오도록 */
    fun read(context: Context): List<String> = synchronized(lock) {
        runCatching { file(context).readLines().filter { it.isNotBlank() }.reversed() }.getOrDefault(emptyList())
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
