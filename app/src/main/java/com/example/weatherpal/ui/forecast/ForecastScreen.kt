package com.example.weatherpal.ui.forecast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.ui.*
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    state: ForecastState,
    clock: Clock,
    onSelect: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
) {
    val shortViewport = LocalConfiguration.current.screenHeightDp < 500
    val compact = shortViewport || LocalDensity.current.fontScale > 1.5f
    when (val content = state.content) {
        is ForecastContent.InitialLoading ->
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 2.dp)
                    Text(
                        "A little inspiration for ${content.city.name}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Finding the weather fit for your week…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        is ForecastContent.InitialError ->
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                StatusPanel("Let’s try that again", content.failure.message(), Symbol.CLOUD) {
                    RetryButton(content.failure, clock, onRefresh)
                }
            }
        is ForecastContent.Ready ->
            key(content.city.id) {
                val failure = (state.refresh as? RefreshStatus.Failed)?.failure
                PullToRefreshBox(
                    isRefreshing = state.refresh is RefreshStatus.Refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(
                                    start = 24.dp,
                                    end = 16.dp,
                                    top = if (shortViewport) 0.dp else 6.dp,
                                    bottom = if (shortViewport) 6.dp else 16.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (shortViewport && LocalDensity.current.fontScale <= 1.3f)
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        content.city.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    Text(
                                        content.city.description(),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            else
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    if (!compact) Eyebrow("YOUR NEXT DAY OUT")
                                    Text(
                                        content.city.name,
                                        style =
                                            if (compact) MaterialTheme.typography.headlineSmall
                                            else MaterialTheme.typography.headlineLarge,
                                    )
                                    if (content.city.description().isNotBlank())
                                        Text(
                                            content.city.description(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                }
                        }
                        if (failure != null) {
                            Surface(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Row(
                                    Modifier.padding(
                                        start = 12.dp,
                                        end = 4.dp,
                                        top = 6.dp,
                                        bottom = 6.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        failure.message() + " Showing your saved forecast.",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    RetryButton(failure, clock, onRefresh)
                                }
                            }
                        }
                        DateCarousel(content, onSelect, Modifier.weight(1f), compact)
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
    compact: Boolean,
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
        val index = dates.indexOf(ready.selectedDate).coerceAtLeast(0)
        val visible = timeline.layoutInfo.visibleItemsInfo
        // Keep the strip still when the selected tile is already completely visible.
        if (
            visible.none {
                it.index == index &&
                    it.offset >= 0 &&
                    it.offset + it.size <= timeline.layoutInfo.viewportEndOffset
            }
        )
            timeline.animateScrollToItem(index)
    }
    val holder = rememberSaveableStateHolder()
    Column(modifier) {
        if (!compact)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "7-day outlook",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    ready.selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        if (!compact) Spacer(Modifier.height(10.dp))
        LazyRow(
            state = timeline,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(ready.days, key = { it.date.toString() }) { day ->
                val selected = day.date == ready.selectedDate
                Surface(
                    color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                    contentColor =
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier =
                        Modifier.widthIn(min = 52.dp).selectableDate(selected, day.date) {
                            select(day.date)
                        },
                ) {
                    if (compact)
                        Row(
                            Modifier.heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (day.date == dates.first()) "Today"
                                else day.date.format(DateTimeFormatter.ofPattern("EEE")),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                day.date.format(DateTimeFormatter.ofPattern("dd")),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    else
                        Column(
                            Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                if (day.date == dates.first()) "Today"
                                else day.date.format(DateTimeFormatter.ofPattern("EEE")),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                day.date.format(DateTimeFormatter.ofPattern("dd")),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                day.weather?.temperatureC?.let { "${it.roundToInt()}°" } ?: "—",
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (selected)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalPager(
            state = pager,
            key = { "${ready.city.id}-${dates[it]}" },
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Top,
        ) { index ->
            val day = ready.days[index]
            holder.SaveableStateProvider("${ready.city.id}-${day.date}") { DailyPage(day, ready) }
        }
    }
}

private fun Modifier.selectableDate(selected: Boolean, date: LocalDate, onClick: () -> Unit) =
    clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        .clickable(role = Role.Tab, onClick = onClick)
        .semantics(mergeDescendants = true) {
            this.selected = selected
            contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
            stateDescription = if (selected) "Selected date" else "Select date"
        }

@Composable
private fun DailyPage(day: ForecastDayUi, ready: ForecastContent.Ready) {
    Column(
        Modifier.fillMaxSize()
            .testTag("forecast-${day.date}")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (day.weather == null) {
            StatusPanel(
                "No forecast for this day yet",
                "Refresh to check for an update, or choose another day. Activity recommendations need weather data for this exact date.",
                Symbol.CALENDAR,
            )
            Activity.entries.forEach { activity ->
                key(activity) {
                    ActivityCard(
                        Recommendation(
                            activity,
                            null,
                            Suitability.INSUFFICIENT_DATA,
                            listOf(Reason(ReasonCode.MISSING_INPUTS)),
                        ),
                        activity.ordinal + 1,
                    )
                }
            }
        } else {
            WeatherOverview(day.weather)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeading("Make a day of it", "4 activities")
                Text(
                    "Ranked by weather fit. Tap a card to explore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.recommendations.forEachIndexed { index, recommendation ->
                key(recommendation.activity) { ActivityCard(recommendation, index + 1) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallNote(
                "Weather-only suggestions. Check local availability and safety before heading out. Activity images are illustrative."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AppIcon(
                    Symbol.CLOCK,
                    Modifier.padding(top = 2.dp).size(14.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Last successful update: ${updateFormat.withZone(ready.city.zone).format(ready.lastSuccessfulUpdate)}\n${ready.city.timezone} · Whole-day forecast, including elapsed hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun WeatherOverview(weather: DailyWeather) {
    var snowDetails by rememberSaveable { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column {
        Box(
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(Color(0xFF173F35), Color(0xFF326950))))
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        weather.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        Modifier.weight(1f),
                        color = Color(0xFFE1EDDF),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    AppIcon(Symbol.CALENDAR, Modifier.size(16.dp), Color(0xFFA9C9AF))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            weather.temperatureC?.let { "${it.roundToInt()}°C" } ?: "—",
                            color = Color.White,
                            style =
                                if (largeText) MaterialTheme.typography.displayMedium
                                else MaterialTheme.typography.displayLarge,
                        )
                        Text(
                            "Mean temperature",
                            color = Color(0xFFD2E3D4),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    // Decorative planning motif; no cloud cover or hourly conditions are inferred.
                    if (!largeText)
                        Box(
                            Modifier.size(78.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.07f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(Symbol.COMPASS, Modifier.size(48.dp), Color(0xFFE7D9A0))
                        }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                if (largeText)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WeatherMetric(
                            Symbol.DROP,
                            number(weather.precipitationMm, "mm"),
                            "Precipitation",
                            Modifier.fillMaxWidth(),
                        )
                        WeatherMetric(
                            Symbol.WIND,
                            number(weather.windKmh, "km/h"),
                            "Maximum wind",
                            Modifier.fillMaxWidth(),
                        )
                    }
                else
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        WeatherMetric(
                            Symbol.DROP,
                            number(weather.precipitationMm, "mm"),
                            "Precipitation",
                            Modifier.weight(1f),
                        )
                        WeatherMetric(
                            Symbol.WIND,
                            number(weather.windKmh, "km/h"),
                            "Maximum wind",
                            Modifier.weight(1f),
                        )
                    }
            }
        }
        TextButton(
            onClick = { snowDetails = !snowDetails },
            modifier =
                Modifier.align(Alignment.End).semantics {
                    stateDescription = if (snowDetails) "Expanded" else "Collapsed"
                },
        ) {
            AppIcon(Symbol.SNOW, Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (snowDetails) "Hide snow details" else "Snow details",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(4.dp))
            AppIcon(if (snowDetails) Symbol.CLOSE else Symbol.CHEVRON, Modifier.size(14.dp))
        }
        AnimatedVisibility(snowDetails) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Median snow depth · ${number(weather.snowDepthCm, "cm")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Snowfall · ${number(weather.snowfallCm, "cm")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Modeled daily values; check actual conditions locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(symbol: Symbol, value: String, label: String, modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(symbol, Modifier.size(21.dp), Color(0xFFB5D0B6))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(label, color = Color(0xFFD2E3D4), style = MaterialTheme.typography.bodySmall)
        }
    }
}
