package com.ghwidget.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ghwidget.app.R
import com.ghwidget.app.ui.components.AppIcons
import com.ghwidget.app.ui.components.BottomNavBar
import com.ghwidget.app.ui.components.NavItem
import com.ghwidget.app.ui.theme.AppTheme


/** 로그인 전에는 소개·로그인 화면, 로그인 후에는 하단 탭 3개 */
@Composable
fun MainScreen(state: HomeState, actions: HomeActions, initialTab: Tab = Tab.Dashboard) {
    if (state.account !is Account.SignedIn) {
        OnboardingScreen(state, actions)
        return
    }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    // 뒤로 가기: 다른 탭이면 대시보드로, 대시보드면 앱 종료
    BackHandler(enabled = tab != Tab.Dashboard) { tab = Tab.Dashboard }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.Dashboard -> DashboardTab(state, actions)
                Tab.Widget -> WidgetTab(state, actions)
                Tab.Settings -> SettingsTab(state, actions)
            }
        }
        BottomNavBar(
            listOf(
                NavItem(stringResource(R.string.tab_dashboard), AppIcons.Dashboard),
                NavItem(stringResource(R.string.tab_widget), AppIcons.Widget),
                NavItem(stringResource(R.string.tab_settings), AppIcons.Settings),
            ),
            tab.ordinal,
        ) { tab = Tab.entries[it] }
    }
}

/** 탭 화면 공통 틀: 상태 표시줄 여백 + 세로 스크롤 + 간격 */
@Composable
fun TabPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}
