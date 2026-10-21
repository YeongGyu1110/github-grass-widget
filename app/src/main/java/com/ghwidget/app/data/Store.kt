package com.ghwidget.app.data

import android.content.Context
import androidx.core.content.edit
import java.io.File

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class PaletteId(val label: String) {
    GREEN("그린"), BLUE("블루"), PURPLE("퍼플"), ORANGE("오렌지")
}

data class Settings(
    val username: String = "",
    val token: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val palette: PaletteId = PaletteId.GREEN,
    /** 배경 불투명도 0~100 */
    val bgOpacity: Int = 100,
    val showHeader: Boolean = true,
    val showMonths: Boolean = true,
    val refreshHours: Int = 3,
)

/** 설정(SharedPreferences) + 마지막으로 받아온 데이터 캐시(파일) */
class Store(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val cacheFile = File(app.filesDir, "contributions.json")

    fun settings(): Settings = Settings(
        username = prefs.getString("username", "") ?: "",
        token = prefs.getString("token", "") ?: "",
        theme = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM),
        palette = enumOr(prefs.getString("palette", null), PaletteId.GREEN),
        bgOpacity = prefs.getInt("bgOpacity", 100),
        showHeader = prefs.getBoolean("showHeader", true),
        showMonths = prefs.getBoolean("showMonths", true),
        refreshHours = prefs.getInt("refreshHours", 3),
    )

    fun save(s: Settings) = prefs.edit {
        putString("username", s.username.trim())
        putString("token", s.token.trim())
        putString("theme", s.theme.name)
        putString("palette", s.palette.name)
        putInt("bgOpacity", s.bgOpacity)
        putBoolean("showHeader", s.showHeader)
        putBoolean("showMonths", s.showMonths)
        putInt("refreshHours", s.refreshHours)
    }

    /** 현재 설정의 사용자와 일치하는 캐시만 돌려준다 */
    fun cachedData(): ContributionData? {
        val data = runCatching { ContributionData.fromJson(cacheFile.readText()) }.getOrNull() ?: return null
        return data.takeIf { it.username.equals(settings().username, ignoreCase = true) }
    }

    /** etag: 이 데이터를 받은 HTML 응답의 ETag (다음 요청에서 바뀌었는지 확인하는 데 씀) */
    fun saveData(data: ContributionData, etag: String?) {
        cacheFile.writeText(data.toJson())
        prefs.edit {
            putString("lastError", null)
            putString("etag", etag)
            putString("etagUser", data.username)
        }
    }

    /** 캐시와 짝이 맞는 ETag. 사용자가 바뀌었으면 null */
    fun etagFor(username: String): String? =
        prefs.getString("etag", null)
            ?.takeIf { prefs.getString("etagUser", null).equals(username, ignoreCase = true) }

    var lastError: String?
        get() = prefs.getString("lastError", null)
        set(v) = prefs.edit { putString("lastError", v) }

    var lastAttemptAt: Long
        get() = prefs.getLong("lastAttemptAt", 0L)
        set(v) = prefs.edit { putLong("lastAttemptAt", v) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
