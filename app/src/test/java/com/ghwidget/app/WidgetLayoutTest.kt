package com.ghwidget.app

import android.widget.FrameLayout
import android.widget.RemoteViews
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 런처는 RemoteViews 로 위젯 레이아웃을 inflate 하며, 허용된 뷰 클래스(@RemoteView)만 쓸 수 있다.
 * 허용되지 않은 클래스가 있으면 홈 화면에 위젯을 추가할 수 없으므로 여기서 미리 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetLayoutTest {

    private fun inflate(layout: Int) {
        val context = RuntimeEnvironment.getApplication()
        RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
    }

    @Test
    fun widgetLayoutInflatesAsRemoteViews() = inflate(R.layout.widget_contributions)

    @Test
    @Config(qualifiers = "night")
    fun nightWidgetLayoutInflatesAsRemoteViews() = inflate(R.layout.widget_contributions)

    @Test
    fun previewLayoutInflatesAsRemoteViews() = inflate(R.layout.widget_preview)
}
