package com.example.weatherpal.ui.search

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.ui.search.*
import com.example.weatherpal.ui.theme.WeatherPalTheme
import java.time.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun recentPlacesRetryIsSeparateFromSearchRetryAndRetainsSavedPlaces() {
        val city = City(1, "Berlin", null, "Germany", 52.0, 13.0, "Europe/Berlin")
        var state by
            mutableStateOf(
                SearchState(
                    recent = listOf(CachedCitySummary(city, Instant.EPOCH)),
                    cacheFailure = AppFailure(FailureKind.STORAGE),
                )
            )
        var cacheRetries = 0
        var searches = 0
        compose.setContent {
            WeatherPalTheme {
                SearchScreen(
                    state,
                    Clock.systemUTC(),
                    {},
                    { searches++ },
                    { searches++ },
                    {
                        cacheRetries++
                        state = state.copy(cacheFailure = null)
                    },
                    {},
                )
            }
        }
        compose.onNodeWithText("Retry recent places").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, cacheRetries)
            assertEquals(0, searches)
        }
        compose.onNodeWithText("Retry recent places").assertDoesNotExist()
        compose.onNodeWithText("Berlin").performScrollTo().assertIsDisplayed()
    }
}
