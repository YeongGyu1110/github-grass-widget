package com.ghwidget.app.data

import java.net.HttpURLConnection
import java.net.URL

internal object Http {

    class Response(val code: Int, val body: String)

    /** 작은 파일(프로필 이미지 등)을 받는다. maxBytes 를 넘으면 실패 */
    fun getBytes(url: String, maxBytes: Int = 1_000_000): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "GithubContributionsWidget/1.0 (Android)")
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val bytes = conn.inputStream.use { it.readNBytesCompat(maxBytes + 1) }
            if (bytes.size > maxBytes) throw java.io.IOException("파일이 너무 큽니다")
            return bytes
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun post(url: String, body: String, headers: Map<String, String>): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "GithubContributionsWidget/1.0 (Android)")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return Response(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }
}
