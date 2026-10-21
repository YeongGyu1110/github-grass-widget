package com.ghwidget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.insights
import com.ghwidget.app.ui.charts.ContributionHeatmap
import com.ghwidget.app.ui.charts.HeatmapLegend
import com.ghwidget.app.ui.charts.MonthlyBars
import com.ghwidget.app.ui.charts.WeekdayBars
import com.ghwidget.app.ui.components.Alert
import com.ghwidget.app.ui.components.AppCard
import com.ghwidget.app.ui.components.AppIconButton
import com.ghwidget.app.ui.components.AppIcons
import com.ghwidget.app.ui.components.CardHeader
import com.ghwidget.app.ui.components.IconTile
import com.ghwidget.app.ui.components.Spinner
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.ui.theme.Radius
import com.ghwidget.app.widget.Palette
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTab(state: HomeState, actions: HomeActions) {
    val account = state.account as? Account.SignedIn
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh) {
        TabPage {
            // 프로필 헤더
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Avatar(account?.login, size = 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        account?.login?.let { "@$it" } ?: "계정 확인 중…",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.foreground,
                    )
                    Text(
                        when {
                            state.refreshing -> "동기화 중…"
                            state.data != null -> "마지막 동기화 ${relativeTime(state.data.fetchedAt)}"
                            else -> "아직 데이터가 없습니다"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedForeground,
                    )
                }
                AppIconButton(AppIcons.Refresh, "새로고침", actions.onRefresh, loading = state.refreshing)
            }

            if (state.error != null) Alert("동기화하지 못했습니다", state.error)

            val data = state.data
            if (data == null || data.days.isEmpty()) {
                LoadingCard()
            } else {
                Dashboard(data, state)
            }
        }
    }
}

@Composable
private fun Dashboard(data: ContributionData, state: HomeState) {
    val c = AppTheme.colors
    val nf = remember { NumberFormat.getIntegerInstance() }
    val levels = Palette.of(state.settings.palette, dark = c.isDark).levels
    val insights = remember(data) { data.insights() }
    var selected by rememberSaveable(data.fetchedAt) { mutableStateOf<String?>(null) }
    val selectedDay = selected?.let { data.byDate[LocalDate.parse(it)] }

    // 잔디 그래프
    AppCard {
        CardHeader("기여 그래프", "최근 1년 동안 ${nf.format(data.total)}회 기여했어요")
        ContributionHeatmap(data, levels, selectedDay?.date) { day -> selected = day?.date?.toString() }
        // 범례는 오른쪽 위, 선택한 날짜 정보는 그 아래 한 줄
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { HeatmapLegend(levels) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.control)
                    .background(c.muted)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedDay?.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        ?: "칸을 누르면 날짜별 기여를 볼 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedDay != null) c.foreground else c.mutedForeground,
                    modifier = Modifier.weight(1f),
                )
                if (selectedDay != null) {
                    Text(
                        if (selectedDay.count == 0) "기여 없음" else "${nf.format(selectedDay.count)}회",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.foreground,
                    )
                }
            }
        }
    }

    // 통계 2x2
    val best = insights.bestDay
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(AppIcons.Activity, "최근 1년", nf.format(data.total), "회",
                "하루 평균 ${"%.1f".format(insights.dailyAverage)}회", Modifier.weight(1f))
            StatCard(AppIcons.Flame, "연속 기여", data.currentStreak().toString(), "일",
                "오늘 ${data.days.last().count}회", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(AppIcons.Award, "최장 연속", data.longestStreak().toString(), "일",
                "활동한 날 ${insights.activeDays}일", Modifier.weight(1f))
            StatCard(AppIcons.Calendar, "최고 기록", best?.count?.let(nf::format) ?: "0", "회",
                best?.date?.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)) ?: "기록 없음", Modifier.weight(1f))
        }
    }

    // 월별
    val peakMonth = insights.monthly.maxByOrNull { it.second }
    AppCard {
        CardHeader(
            "월별 기여",
            peakMonth?.takeIf { it.second > 0 }?.let { "가장 활발한 달은 ${it.first.monthValue}월이에요 · ${nf.format(it.second)}회" }
                ?: "최근 12개월",
        )
        MonthlyBars(insights.monthly, Color(levels[2]), Color(levels[4]))
    }

    // 요일별
    val peakDay = insights.weekdays.indices.maxByOrNull { insights.weekdays[it] }
    AppCard {
        CardHeader(
            "요일별 패턴",
            peakDay?.takeIf { insights.weekdays[it] > 0 }?.let {
                "${java.time.DayOfWeek.of(if (it == 0) 7 else it).getDisplayName(TextStyle.FULL, Locale.getDefault())}에 가장 많이 기여했어요"
            } ?: "요일별 기여 비율",
        )
        WeekdayBars(insights.weekdays, Color(levels[2]), Color(levels[4]))
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, unit: String, caption: String, modifier: Modifier) {
    val c = AppTheme.colors
    Column(
        modifier
            .clip(Radius.card)
            .background(c.card)
            .border(1.dp, c.border, Radius.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTile(icon, size = 28.dp)
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.mutedForeground)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = c.foreground, maxLines = 1)
                Text(unit, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
            Text(caption, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground, maxLines = 1)
        }
    }
}

@Composable
private fun LoadingCard() {
    val c = AppTheme.colors
    AppCard {
        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spinner(size = 20.dp)
                Text("기여 데이터를 불러오는 중…", style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
            }
        }
    }
}
