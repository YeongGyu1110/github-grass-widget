package com.ghwidget.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ghwidget.app.data.ContributionData
import com.ghwidget.app.data.insights
import com.ghwidget.app.R
import com.ghwidget.app.ui.charts.ContributionHeatmap
import com.ghwidget.app.ui.charts.HeatmapLegend
import com.ghwidget.app.ui.charts.MonthlyBars
import com.ghwidget.app.ui.charts.WeekdayBars
import com.ghwidget.app.ui.components.Alert
import com.ghwidget.app.ui.components.AppCard
import com.ghwidget.app.ui.components.AppIcons
import com.ghwidget.app.ui.components.CardHeader
import com.ghwidget.app.ui.components.IconTile
import com.ghwidget.app.ui.components.Spinner
import com.ghwidget.app.ui.theme.AppTheme
import com.ghwidget.app.ui.theme.Radius
import com.ghwidget.app.widget.Palette
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTab(state: HomeState, actions: HomeActions) {
    val account = state.account as? Account.SignedIn
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh) {
        TabPage {
            // 프로필 헤더
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Avatar(account?.login, state.avatar, size = 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        account?.login?.let { "@$it" } ?: stringResource(R.string.checking_account),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.foreground,
                    )
                    Text(
                        when {
                            state.refreshing -> stringResource(R.string.syncing)
                            state.data != null -> stringResource(R.string.dashboard_last_synced, relativeTime(LocalContext.current, state.data.fetchedAt))
                            else -> stringResource(R.string.no_data_yet)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedForeground,
                    )
                }
            }

            if (state.error != null) Alert(stringResource(R.string.sync_failed), state.error)

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
        CardHeader(stringResource(R.string.graph_title), pluralResource(R.plurals.graph_subtitle, data.total, nf.format(data.total)))
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
                        ?: stringResource(R.string.graph_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedDay != null) c.foreground else c.mutedForeground,
                    modifier = Modifier.weight(1f),
                )
                if (selectedDay != null) {
                    Text(
                        if (selectedDay.count == 0) stringResource(R.string.day_no_contributions)
                        else pluralResource(R.plurals.day_contributions, selectedDay.count, nf.format(selectedDay.count)),
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
            val streak = data.currentStreak()
            StatCard(AppIcons.Activity, stringResource(R.string.stat_last_year), nf.format(data.total), stringResource(R.string.unit_count),
                stringResource(R.string.stat_daily_average, "%.1f".format(insights.dailyAverage)), Modifier.weight(1f))
            StatCard(AppIcons.Flame, stringResource(R.string.stat_current_streak), streak.toString(), pluralResource(R.plurals.unit_days, streak),
                stringResource(R.string.stat_today, data.days.last().count), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val longest = data.longestStreak()
            StatCard(AppIcons.Award, stringResource(R.string.stat_longest_streak), longest.toString(), pluralResource(R.plurals.unit_days, longest),
                pluralResource(R.plurals.stat_active_days, insights.activeDays, insights.activeDays), Modifier.weight(1f))
            // 날짜는 언어에 맞는 "월 일" 형식 (한국어: 10월 24일, 영어: Oct 24)
            val monthDay = remember { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")) }
            StatCard(AppIcons.Calendar, stringResource(R.string.stat_best_day), best?.count?.let(nf::format) ?: "0", stringResource(R.string.unit_count),
                best?.date?.format(monthDay) ?: stringResource(R.string.stat_no_record), Modifier.weight(1f))
        }
    }

    // 월별
    val peakMonth = insights.monthly.maxByOrNull { it.second }
    AppCard {
        CardHeader(
            stringResource(R.string.monthly_title),
            peakMonth?.takeIf { it.second > 0 }?.let {
                stringResource(R.string.monthly_peak, it.first.month.getDisplayName(TextStyle.FULL, Locale.getDefault()), nf.format(it.second))
            } ?: stringResource(R.string.monthly_default),
        )
        MonthlyBars(insights.monthly, Color(levels[2]), Color(levels[4]))
    }

    // 요일별
    val peakDay = insights.weekdays.indices.maxByOrNull { insights.weekdays[it] }
    AppCard {
        CardHeader(
            stringResource(R.string.weekday_title),
            peakDay?.takeIf { insights.weekdays[it] > 0 }?.let {
                stringResource(R.string.weekday_peak, java.time.DayOfWeek.of(if (it == 0) 7 else it).getDisplayName(TextStyle.FULL, Locale.getDefault()))
            } ?: stringResource(R.string.weekday_default),
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
                Text(stringResource(R.string.loading_contributions), style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
            }
        }
    }
}
