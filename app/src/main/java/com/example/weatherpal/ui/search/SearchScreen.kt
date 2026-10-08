package com.example.weatherpal.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.weatherpal.domain.model.City
import com.example.weatherpal.ui.*
import java.time.Clock

@Composable
fun SearchScreen(
    state: SearchState,
    clock: Clock,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onCity: (City) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var input by rememberSaveable { mutableStateOf(state.query) }
    val submit = {
        keyboard?.hide()
        onEdit(input)
        onSubmit()
    }
    val remaining = retrySeconds((state.status as? SearchStatus.Error)?.failure, clock)
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Find your next day out", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Search a city to compare four activities across seven days.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    onEdit(it)
                },
                label = { Text("City name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = submit,
                enabled = remaining == 0L,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (remaining > 0) "Search available in ${remaining}s" else "Search cities")
            }
        }
        when (val status = state.status) {
            SearchStatus.Idle -> Unit
            is SearchStatus.Loading ->
                item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Searching for “${status.query}”…")
                }
            is SearchStatus.Empty ->
                item { Text("No cities found for “${status.query}”. Try a different spelling.") }
            is SearchStatus.Error ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(status.failure.message())
                            if (
                                status.failure.kind !=
                                    com.example.weatherpal.domain.model.FailureKind.VALIDATION
                            )
                                RetryButton(status.failure, clock, onRetry)
                        }
                    }
                }
            is SearchStatus.Results -> {
                item {
                    Text(
                        "Matches for “${status.query}”",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(status.cities, key = { "result-${it.id}" }) { city ->
                    CityCard(city, "View seven-day rankings") { onCity(city) }
                }
            }
        }
        item { Text("Recent cached cities", style = MaterialTheme.typography.titleLarge) }
        if (state.recent.isEmpty())
            item {
                Text(
                    "Cities appear here after a successful forecast update. Up to three are available offline."
                )
            }
        items(state.recent, key = { "cached-${it.city.id}" }) { row ->
            CityCard(
                row.city,
                "Last successful update: ${updateFormat.withZone(row.city.zone).format(row.lastSuccessfulUpdate)}",
            ) {
                onCity(row.city)
            }
        }
        state.cacheFailure?.let { failure ->
            item { Text(failure.message(), color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text(
                "Weather-only suggestions. Conditions and activity availability can vary.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CityCard(city: City, detail: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(city.name, style = MaterialTheme.typography.titleMedium)
            if (city.description().isNotBlank()) Text(city.description())
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
