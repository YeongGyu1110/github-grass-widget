package com.ghwidget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import com.ghwidget.app.MainActivity
import com.ghwidget.app.R
import com.ghwidget.app.data.Store
import com.ghwidget.app.data.ThemeMode

object WidgetUpdater {

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ContributionWidgetProvider::class.java))
        ids.forEach { update(context, manager, it) }
    }

    /**
     * 위젯은 그래프가 바뀔 때만 다시 그리므로, 휴대폰 언어나 앱 언어가 바뀌면 직접 다시 그린다.
     * (휴대폰 언어: LOCALE_CHANGED 방송, 앱 언어(Android 13+): 앱을 다시 열 때)
     */
    fun updateIfLocaleChanged(context: Context) {
        val store = Store(context)
        val tag = context.resources.configuration.locales[0].toLanguageTag()
        if (store.widgetLocale == tag) return
        store.widgetLocale = tag
        updateAll(context)
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val store = Store(context)
        val settings = store.settings()
        val data = store.cachedData()
        val message = when {
            store.accessToken == null -> context.getString(
                if (store.signOutNotice != null) R.string.widget_sign_in_again else R.string.widget_sign_in,
            )
            data == null -> store.lastError ?: context.getString(R.string.loading)
            else -> null
        }

        val (wDp, hDp) = widgetSizeDp(context, manager, widgetId)
        val density = context.resources.displayMetrics.density
        fun draw(dark: Boolean) =
            GraphRenderer.render(context.resources, wDp, hDp, density, dark, settings, data, message)

        val light: android.graphics.Bitmap
        val dark: android.graphics.Bitmap
        when (settings.theme) {
            ThemeMode.SYSTEM -> { light = draw(false); dark = draw(true) }
            ThemeMode.LIGHT -> { light = draw(false); dark = light }
            ThemeMode.DARK -> { dark = draw(true); light = dark }
        }

        val views = RemoteViews(context.packageName, R.layout.widget_contributions).apply {
            setImageViewBitmap(R.id.img_light, light)
            setImageViewBitmap(R.id.img_dark, dark)
            setOnClickPendingIntent(R.id.click_header, openAppIntent(context))
            // 로그아웃 상태면 그래프를 눌러도 새로고침 대신 로그인 화면(앱)을 연다
            setOnClickPendingIntent(
                R.id.click_graph,
                if (store.accessToken == null) openAppIntent(context) else refreshIntent(context),
            )
            setViewVisibility(R.id.click_header, if (settings.showHeader) View.VISIBLE else View.GONE)
        }
        manager.updateAppWidget(widgetId, views)
    }

    /** 런처가 알려주는 위젯 크기(dp). 세로 화면: minWidth x maxHeight, 가로 화면: maxWidth x minHeight */
    private fun widgetSizeDp(context: Context, manager: AppWidgetManager, id: Int): Pair<Float, Float> {
        val o = manager.getAppWidgetOptions(id)
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return (if (w > 0) w.toFloat() else 250f) to (if (h > 0) h.toFloat() else 110f)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, ContributionWidgetProvider::class.java).setAction(ContributionWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
