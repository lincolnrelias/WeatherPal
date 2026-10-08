package com.example.weatherpal.ui.forecast

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.ui.*
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    state: ForecastState,
    clock: Clock,
    onSelect: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
) {
    when (val content = state.content) {
        is ForecastContent.InitialLoading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Loading ${content.city.name}…")
                }
            }
        is ForecastContent.InitialError ->
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Forecast unavailable", style = MaterialTheme.typography.headlineSmall)
                Text(content.failure.message())
                RetryButton(content.failure, clock, onRefresh)
            }
        is ForecastContent.Ready ->
            key(content.city.id) {
                val failure = (state.refresh as? RefreshStatus.Failed)?.failure
                val retryBlocked = retrySeconds(failure, clock) > 0L
                PullToRefreshBox(
                    isRefreshing = state.refresh is RefreshStatus.Refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        content.city.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                    Text(
                                        content.city.description(),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(
                                    onClick = onRefresh,
                                    enabled =
                                        state.refresh !is RefreshStatus.Refreshing && !retryBlocked,
                                ) {
                                    Text("Refresh")
                                }
                            }
                            Text(
                                "Last successful update: ${updateFormat.withZone(content.city.zone).format(content.lastSuccessfulUpdate)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${content.city.timezone} · whole-day forecast",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (failure != null)
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        failure.message() + " Cached content is retained.",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    RetryButton(failure, clock, onRefresh)
                                }
                            }
                        Text(
                            "Weather-only rankings; availability and safety are not assessed.",
                            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        DateCarousel(content, onSelect, Modifier.weight(1f))
                    }
                }
            }
        null -> Unit
    }
}

@Composable
private fun DateCarousel(
    ready: ForecastContent.Ready,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val dates = ready.days.map { it.date }
    val pager =
        rememberPagerState(
            initialPage = dates.indexOf(ready.selectedDate).coerceAtLeast(0),
            pageCount = { dates.size },
        )
    var synchronizing by remember { mutableStateOf(false) }
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(ready.selectedDate, dates) {
        synchronizing = true
        try {
            val target = dates.indexOf(ready.selectedDate)
            if (target >= 0 && pager.currentPage != target) pager.animateScrollToPage(target)
        } finally {
            synchronizing = false
        }
    }
    LaunchedEffect(pager, dates) {
        snapshotFlow { Triple(pager.settledPage, pager.isScrollInProgress, synchronizing) }
            .filter { !it.second && !it.third }
            .collect { dates.getOrNull(it.first)?.let(select) }
    }
    val timeline = rememberLazyListState()
    LaunchedEffect(ready.selectedDate, dates) {
        timeline.animateScrollToItem(dates.indexOf(ready.selectedDate).coerceAtLeast(0))
    }
    val holder = rememberSaveableStateHolder()
    Column(modifier) {
        LazyRow(
            state = timeline,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(dates, key = { it.toString() }) { date ->
                FilterChip(
                    selected = date == ready.selectedDate,
                    onClick = { select(date) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription =
                                date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                            stateDescription =
                                if (date == ready.selectedDate) "Selected date" else "Select date"
                        },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.format(DateTimeFormatter.ofPattern("EEE")))
                            Text(date.format(DateTimeFormatter.ofPattern("MMM d")))
                        }
                    },
                )
            }
        }
        HorizontalPager(
            state = pager,
            key = { "${ready.city.id}-${dates[it]}" },
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Top,
        ) { index ->
            val day = ready.days[index]
            holder.SaveableStateProvider("${ready.city.id}-${day.date}") { DailyPage(day) }
        }
    }
}

@Composable
private fun DailyPage(day: ForecastDayUi) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            day.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleLarge,
        )
        val weather = day.weather
        if (weather == null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Forecast unavailable for this date",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Refresh to request current data. Another day’s weather is never substituted."
                    )
                    Activity.entries.forEach {
                        Text("${it.title()} · Insufficient data", Modifier.padding(top = 12.dp))
                    }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The day’s weather", style = MaterialTheme.typography.titleMedium)
                    Text("Mean temperature · ${number(weather.temperatureC,"°C")}")
                    Text("Precipitation · ${number(weather.precipitationMm,"mm")}")
                    Text("Maximum wind · ${number(weather.windKmh,"km/h")}")
                    Text("Median snow depth · ${number(weather.snowDepthCm,"cm")}")
                    Text("Snowfall · ${number(weather.snowfallCm,"cm")}")
                }
            }
            day.recommendations.forEachIndexed { index, recommendation ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "${index+1}. ${recommendation.activity.title()}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            recommendation.label.title(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        recommendation.reasons.forEach { reason ->
                            Text(
                                reasonText(reason, recommendation.activity),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (recommendation.activity == Activity.SURFING)
                            Text(
                                "Land-weather comfort only. Waves, water temperature, shore wind direction, tides, and surfability are not assessed.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                }
            }
        }
    }
}
