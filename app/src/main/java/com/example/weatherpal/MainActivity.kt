package com.example.weatherpal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                BackHandler(enabled = forecastState.city != null) { forecast.leave() }
                Scaffold(
                    topBar = {
                        AppBar(forecastState.city != null, { forecast.leave() }, { about = true })
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(canGoBack: Boolean, onBack: () -> Unit, onAbout: () -> Unit) {
    TopAppBar(
        title = { Text("WeatherPal") },
        navigationIcon = { if (canGoBack) TextButton(onClick = onBack) { Text("Search") } },
        actions = { TextButton(onClick = onAbout) { Text("About") } },
    )
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
