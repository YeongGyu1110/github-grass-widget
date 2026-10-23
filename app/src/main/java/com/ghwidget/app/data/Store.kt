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
    /** 이 앱 화면의 테마 (위젯 테마와 별개) */
    val appTheme: ThemeMode = ThemeMode.SYSTEM,
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
        appTheme = enumOr(prefs.getString("appTheme", null), ThemeMode.SYSTEM),
    )

    fun save(s: Settings) = prefs.edit {
        putString("theme", s.theme.name)
        putString("palette", s.palette.name)
        putInt("bgOpacity", s.bgOpacity)
        putBoolean("showHeader", s.showHeader)
        putBoolean("showMonths", s.showMonths)
        putInt("refreshHours", s.refreshHours)
        putString("appTheme", s.appTheme.name)
    }

    // ------------------------------------------------------------ 로그인

    /** GitHub OAuth 액세스 토큰. null 이면 로그아웃 상태 */
    val accessToken: String? get() = prefs.getString("accessToken", null)

    /** access token 갱신용 토큰 (짧은 수명 토큰을 쓰는 OAuth App 일 때만 있음) */
    val refreshToken: String? get() = prefs.getString("refreshToken", null)

    /** access token 만료 시각 (epoch ms). 0 이면 만료 없음/모름 */
    val accessExpiresAt: Long get() = prefs.getLong("accessExpiresAt", 0L)

    /** refresh token 만료 시각 (epoch ms). 0 이면 모름 */
    val refreshExpiresAt: Long get() = prefs.getLong("refreshExpiresAt", 0L)

    /** 자동으로 로그아웃된 이유 (로그인 화면에 보여준다). 다시 로그인하면 지워짐 */
    var signOutNotice: String?
        get() = prefs.getString("signOutNotice", null)
        set(v) = prefs.edit(commit = true) { putString("signOutNotice", v) }

    /**
     * 갱신한 토큰 묶음 저장. refresh token 은 한 번 쓰면 무효가 되므로
     * 저장이 날아가면 다시 로그인해야 한다 → 디스크에 바로 쓴다(commit).
     */
    fun saveTokens(t: GitHubAuth.TokenSet) = prefs.edit(commit = true) { putTokens(t) }

    private fun android.content.SharedPreferences.Editor.putTokens(t: GitHubAuth.TokenSet) {
        putString("accessToken", t.accessToken)
        putString("refreshToken", t.refreshToken)
        putLong("accessExpiresAt", t.accessExpiresAt ?: 0L)
        putLong("refreshExpiresAt", t.refreshExpiresAt ?: 0L)
    }

    /** 로그인한 GitHub 계정 이름. 로그인 직후 첫 데이터를 받기 전에는 null */
    var login: String?
        get() = prefs.getString("login", null)
        set(v) = prefs.edit { putString("login", v) }

    /** 진행 중인 Device Flow 로그인 (브라우저에 다녀오는 동안 앱이 종료돼도 이어서 확인하려고 저장) */
    var pendingLogin: GitHubAuth.DeviceCode?
        get() = prefs.getString("pendingLogin", null)?.let { runCatching { GitHubAuth.DeviceCode.fromJson(it) }.getOrNull() }
        set(v) = prefs.edit { putString("pendingLogin", v?.toJson()) }

    // ------------------------------------------------------------ 프로필 이미지

    /** 받아 둔 프로필 이미지 파일 (없을 수 있음) */
    val avatarFile: File get() = File(app.filesDir, "avatar")

    /** 지금 저장된 프로필 이미지를 받은 주소. 주소가 바뀌었을 때만 다시 받는다 */
    var avatarUrl: String?
        get() = prefs.getString("avatarUrl", null)
        set(v) = prefs.edit { putString("avatarUrl", v) }

    fun needsAvatar(url: String): Boolean = url != avatarUrl || !avatarFile.exists()

    fun saveAvatar(url: String, bytes: ByteArray) {
        avatarFile.writeBytes(bytes)
        avatarUrl = url
    }

    private fun clearAvatar() {
        avatarFile.delete()
        prefs.edit { remove("avatarUrl") }
    }

    fun signIn(tokens: GitHubAuth.TokenSet) {
        cacheFile.delete()
        clearAvatar()
        prefs.edit(commit = true) {
            putTokens(tokens)
            remove("signOutNotice")
            remove("login")
            remove("pendingLogin")
            remove("lastError")
        }
    }

    /** notice: 자동 로그아웃일 때 로그인 화면에 보여줄 이유 (사용자가 직접 로그아웃하면 null) */
    fun signOut(notice: String? = null) {
        cacheFile.delete()
        clearAvatar()
        prefs.edit(commit = true) {
            remove("accessToken")
            remove("refreshToken")
            remove("accessExpiresAt")
            remove("refreshExpiresAt")
            putString("signOutNotice", notice)
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
