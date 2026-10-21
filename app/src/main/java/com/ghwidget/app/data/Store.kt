package com.ghwidget.app.data

import android.content.Context
import androidx.core.content.edit
import java.io.File

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class PaletteId(val label: String) {
    GREEN("그린"), BLUE("블루"), PURPLE("퍼플"), ORANGE("오렌지")
}

/** 위젯 모양·동작 설정 */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val palette: PaletteId = PaletteId.GREEN,
    /** 배경 불투명도 0~100 */
    val bgOpacity: Int = 100,
    val showHeader: Boolean = true,
    val showMonths: Boolean = true,
    val refreshHours: Int = 3,
)

/** 설정·로그인 정보(SharedPreferences) + 마지막으로 받아온 데이터 캐시(파일) */
class Store(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val cacheFile = File(app.filesDir, "contributions.json")

    init {
        // 1.0.2 까지의 사용자 이름 입력 방식에서 남은 값 정리 (평문 토큰 포함)
        if (prefs.contains("username") || prefs.contains("token") || prefs.contains("etag")) {
            prefs.edit { remove("username"); remove("token"); remove("etag"); remove("etagUser") }
        }
    }

    fun settings(): Settings = Settings(
        theme = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM),
        palette = enumOr(prefs.getString("palette", null), PaletteId.GREEN),
        bgOpacity = prefs.getInt("bgOpacity", 100),
        showHeader = prefs.getBoolean("showHeader", true),
        showMonths = prefs.getBoolean("showMonths", true),
        refreshHours = prefs.getInt("refreshHours", 3),
    )

    fun save(s: Settings) = prefs.edit {
        putString("theme", s.theme.name)
        putString("palette", s.palette.name)
        putInt("bgOpacity", s.bgOpacity)
        putBoolean("showHeader", s.showHeader)
        putBoolean("showMonths", s.showMonths)
        putInt("refreshHours", s.refreshHours)
    }

    // ------------------------------------------------------------ 로그인

    /** GitHub OAuth 액세스 토큰. null 이면 로그아웃 상태 */
    val accessToken: String? get() = prefs.getString("accessToken", null)

    /** 로그인한 GitHub 계정 이름. 로그인 직후 첫 데이터를 받기 전에는 null */
    var login: String?
        get() = prefs.getString("login", null)
        set(v) = prefs.edit { putString("login", v) }

    /** 진행 중인 Device Flow 로그인 (브라우저에 다녀오는 동안 앱이 종료돼도 이어서 확인하려고 저장) */
    var pendingLogin: GitHubAuth.DeviceCode?
        get() = prefs.getString("pendingLogin", null)?.let { runCatching { GitHubAuth.DeviceCode.fromJson(it) }.getOrNull() }
        set(v) = prefs.edit { putString("pendingLogin", v?.toJson()) }

    fun signIn(token: String) {
        cacheFile.delete()
        prefs.edit {
            putString("accessToken", token)
            remove("login")
            remove("pendingLogin")
            remove("lastError")
        }
    }

    fun signOut() {
        cacheFile.delete()
        prefs.edit {
            remove("accessToken")
            remove("login")
            remove("pendingLogin")
            remove("lastError")
        }
    }

    // ------------------------------------------------------------ 캐시

    /** 로그인한 계정의 캐시만 돌려준다 */
    fun cachedData(): ContributionData? {
        val login = login ?: return null
        val data = runCatching { ContributionData.fromJson(cacheFile.readText()) }.getOrNull() ?: return null
        return data.takeIf { it.username.equals(login, ignoreCase = true) }
    }

    fun saveData(data: ContributionData) {
        cacheFile.writeText(data.toJson())
        prefs.edit {
            putString("login", data.username)
            remove("lastError")
        }
    }

    var lastError: String?
        get() = prefs.getString("lastError", null)
        set(v) = prefs.edit { putString("lastError", v) }

    var lastAttemptAt: Long
        get() = prefs.getLong("lastAttemptAt", 0L)
        set(v) = prefs.edit { putLong("lastAttemptAt", v) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
