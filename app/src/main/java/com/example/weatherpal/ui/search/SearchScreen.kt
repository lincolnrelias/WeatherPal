package com.example.weatherpal.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.ui.components.*
import com.example.weatherpal.ui.presentation.*
import java.time.Clock

@Composable
fun SearchScreen(
    state: SearchState,
    clock: Clock,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onRetryCachedCities: () -> Unit,
    onCity: (City) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSubmit()
    }
    val remaining = retrySeconds((state.status as? SearchStatus.Error)?.failure, clock)
    val searching = (state.status as? SearchStatus.Loading)?.query == state.query.trim()
    val invalid =
        (state.status as? SearchStatus.Error)?.let { it.failure.kind == FailureKind.VALIDATION } ==
            true
    val listState = rememberLazyListState()
    LaunchedEffect(state.status == SearchStatus.Idle) {
        if (state.status == SearchStatus.Idle) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.status == SearchStatus.Idle) {
            item { SearchHero() }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Where would you like to go?",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "One city. Seven days. Find what feels right.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onEdit,
                    label = { Text("City name") },
                    placeholder = { Text("Try Lisbon or Tokyo") },
                    leadingIcon = {
                        AppIcon(Symbol.SEARCH, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon =
                        if (state.query.isNotEmpty())
                            ({
                                IconButton(onClick = { onEdit("") }) {
                                    AppIcon(Symbol.CLOSE, contentDescription = "Clear city name")
                                }
                            })
                        else null,
                    isError = invalid,
                    supportingText =
                        if (invalid) ({ Text("Enter at least two characters.") }) else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions =
                        KeyboardActions(onSearch = { if (remaining == 0L && !searching) submit() }),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = submit,
                    enabled = remaining == 0L && !searching,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (remaining > 0) "Search available in ${remaining}s"
                        else if (searching) "Searching…" else "Search cities"
                    )
                    if (!searching) {
                        Spacer(Modifier.weight(1f))
                        AppIcon(Symbol.ARROW, Modifier.size(20.dp))
                    }
                }
            }
        }
        when (val status = state.status) {
            SearchStatus.Idle -> Unit
            is SearchStatus.Loading ->
                item {
                    Text(
                        "Looking for “${status.query}”…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            is SearchStatus.Empty ->
                item {
                    StatusPanel(
                        "No city found just yet",
                        "We couldn’t find “${status.query}”. Try a different spelling or a nearby city.",
                        Symbol.SEARCH,
                    )
                }
            is SearchStatus.Error ->
                if (status.failure.kind != FailureKind.VALIDATION)
                    item {
                        StatusPanel("Search is unavailable", status.failure.message()) {
                            RetryButton(status.failure, clock, onRetry)
                        }
                    }
            is SearchStatus.Results -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        SectionHeading("Choose your city", "${status.cities.size} found")
                        Text(
                            "Matches for “${status.query}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(status.cities, key = { "result-${it.id}" }) { city ->
                    CityCard(city, "Explore the 7-day outlook", false) {
                        keyboard?.hide()
                        onCity(city)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                SectionHeading("Your recent places")
                Text(
                    "A little closer to your next adventure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.recent.isEmpty())
            item {
                StatusPanel(
                    "Your places will feel at home here",
                    "Open a city’s forecast to keep it handy. Your three most recently updated places are available offline.",
                    Symbol.LOCATION,
                )
            }
        items(state.recent, key = { "cached-${it.city.id}" }) { row ->
            CityCard(
                row.city,
                "Saved ${updateFormat.withZone(row.city.zone).format(row.lastSuccessfulUpdate)}",
                true,
            ) {
                keyboard?.hide()
                onCity(row.city)
            }
        }
        state.cacheFailure?.let { failure ->
            item {
                StatusPanel("Recent places are unavailable", failure.message()) {
                    TextButton(onClick = onRetryCachedCities) { Text("Retry recent places") }
                }
            }
        }
        item {
            SmallNote(
                "Plans inspired by the weather. Local conditions and activity availability can vary."
            )
        }
    }
}

@Composable
private fun SearchHero() {
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)) {
        ActivityPhoto(Activity.OUTDOOR_SIGHTSEEING, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF102F26).copy(alpha = 0.8f),
                            Color(0xFF102F26).copy(alpha = 0.08f),
                        )
                    )
                )
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF102F26).copy(alpha = 0.5f))
                    )
                )
        )
        Column(
            Modifier.fillMaxWidth().heightIn(min = 248.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Eyebrow("LET THE DAY INSPIRE YOU", color = Color(0xFFE0EACF))
            Spacer(Modifier.height(4.dp))
            Text(
                "Good days\nstart here.",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Text(
                "Find a little inspiration\nin the forecast.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE5EDE0),
            )
        }
    }
}

@Composable
private fun CityCard(city: City, detail: String, cached: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    if (cached) Symbol.CLOCK else Symbol.LOCATION,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(city.name, style = MaterialTheme.typography.titleMedium)
                if (city.description().isNotBlank())
                    Text(
                        city.description(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppIcon(Symbol.ARROW, Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
        }
    }
}
