package com.ghwidget.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.ghwidget.app.data.Store
import com.ghwidget.app.work.Refresh

class ContributionWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.ghwidget.app.action.REFRESH"
        private const val STALE_MS = 30 * 60 * 1000L
        private const val AUTO_RETRY_MS = 5 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            Toast.makeText(context, "GitHub 잔디 새로고침 중…", Toast.LENGTH_SHORT).show()
            Refresh.now(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetUpdater.update(context, manager, it) }
        Refresh.ensurePeriodic(context)

        // 데이터가 오래됐으면 바로 갱신. (WorkManager 가 컴포넌트를 켜고 끌 때도 onUpdate 가 불리므로
        // 실패가 반복될 때 무한 재시도되지 않도록 마지막 시도 시각으로 제한)
        val store = Store(context)
        if (store.settings().username.isBlank()) return
        val data = store.cachedData()
        val now = System.currentTimeMillis()
        val stale = data == null || now - data.fetchedAt > STALE_MS
        if (stale && now - store.lastAttemptAt > AUTO_RETRY_MS) Refresh.now(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle,
    ) {
        WidgetUpdater.update(context, manager, id)
    }

    override fun onEnabled(context: Context) = Refresh.ensurePeriodic(context)

    override fun onDisabled(context: Context) = Refresh.cancelAll(context)
}
