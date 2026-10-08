package com.example.weatherpal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weatherpal.ui.*
import com.example.weatherpal.ui.forecast.*
import com.example.weatherpal.ui.search.*
import com.example.weatherpal.ui.theme.WeatherPalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WeatherPalApplication).container
        setContent {
            WeatherPalTheme {
                val search: SearchViewModel = viewModel(factory = container.factory)
                val forecast: ForecastViewModel = viewModel(factory = container.factory)
                val searchState by search.state.collectAsStateWithLifecycle()
                val forecastState by forecast.state.collectAsStateWithLifecycle()
                var about by remember { mutableStateOf(false) }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner, forecast) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> forecast.onResume()
                            Lifecycle.Event.ON_STOP -> forecast.onBackground()
                            else -> Unit
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                val keyboard = LocalSoftwareKeyboardController.current
                val canGoBack =
                    forecastState.city != null || searchState.status is SearchStatus.Results
                val onBack = {
                    keyboard?.hide()
                    if (forecastState.city != null) forecast.leave() else search.dismissResults()
                }
                BackHandler(enabled = canGoBack, onBack = onBack)
                Scaffold(
                    topBar = {
                        AppBar(
                            canGoBack,
                            onBack,
                            { about = true },
                            if (forecastState.city != null) "Cities" else "Back",
                        )
                    }
                ) { padding ->
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (forecastState.city == null)
                            SearchScreen(
                                searchState,
                                container.clock,
                                search::edit,
                                search::submit,
                                search::retry,
                            ) { city ->
                                search.cancelSearch()
                                forecast.open(city)
                            }
                        else
                            ForecastScreen(
                                forecastState,
                                container.clock,
                                forecast::select,
                                forecast::refresh,
                            )
                    }
                }
                if (about) AboutDialog { about = false }
            }
        }
    }
}

@Composable
private fun AppBar(canGoBack: Boolean, onBack: () -> Unit, onAbout: () -> Unit, backLabel: String) {
    val shortViewport = LocalConfiguration.current.screenHeightDp < 500
    Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 840.dp)
                .fillMaxWidth()
                .heightIn(min = if (shortViewport) 52.dp else 68.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (canGoBack) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    AppIcon(Symbol.BACK, Modifier.size(20.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(backLabel)
                }
                Text(
                    "WeatherPal",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(
                    Modifier.size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(Symbol.SUN, Modifier.size(23.dp), MaterialTheme.colorScheme.onPrimary)
                }
                Text(
                    "WeatherPal",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAbout) {
                AppIcon(
                    Symbol.INFO,
                    Modifier.size(21.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "About WeatherPal",
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About these suggestions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "WeatherPal ranks four activities using deterministic weather heuristics. Labels are not scientific predictions or safety advice. Today includes the whole day, including elapsed hours."
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Skiing uses modeled snow depth, or snowfall when depth is unavailable. Neither verifies slopes, terrain, resorts, or actual snowpack. Surfing reflects land-weather comfort and does not assess ocean conditions or access."
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Indoor sightseeing reflects the relative appeal of indoor plans as outdoor weather worsens, rather than the quality of an attraction. Cached dates may be unavailable; last update means the last successful local save."
                )
                TextButton(onClick = { uri.openUri("https://open-meteo.com/") }) {
                    Text("Weather data by Open-Meteo")
                }
                TextButton(onClick = { uri.openUri("https://www.geonames.org/") }) {
                    Text("Location data by GeoNames")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
