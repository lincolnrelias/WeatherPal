package com.example.weatherpal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.ui.forecast.*
import com.example.weatherpal.ui.theme.WeatherPalTheme
import java.time.*
import java.time.format.DateTimeFormatter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityCardInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val surfing =
        Recommendation(
            Activity.SURFING,
            100,
            Suitability.VERY_FAVORABLE,
            listOf(Reason(ReasonCode.TEMPERATURE, 22.0, 45)),
        )

    private fun state(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun allActivitiesStartCollapsedAndReasonsAreHidden() {
        compose.setContent {
            WeatherPalTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Activity.entries.forEach { activity ->
                        key(activity) {
                            ActivityCard(surfing.copy(activity = activity), activity.ordinal + 1)
                        }
                    }
                }
            }
        }
        compose.onAllNodes(state("Collapsed")).assertCountEquals(4)
        compose.onNodeWithText("BEHIND THE RECOMMENDATION").assertDoesNotExist()
        compose.onAllNodesWithText("Very favorable").assertCountEquals(4)
    }

    @Test
    fun tappingCardExpandsAndCollapsesItsReasons() {
        compose.setContent { WeatherPalTheme { ActivityCard(surfing, 1) } }
        compose.onNodeWithText("Surfing").assert(state("Collapsed")).performClick()
        compose.onNodeWithText("Surfing").assert(state("Expanded"))
        compose.onNodeWithText("BEHIND THE RECOMMENDATION").assertIsDisplayed()
        compose
            .onNodeWithText("Mean 22.0 °C — in the preferred warm band (20 to 30°C).")
            .assertIsDisplayed()
        compose.onNodeWithText("Surfing").performClick()
        compose.onNodeWithText("Surfing").assert(state("Collapsed"))
        compose.onNodeWithText("BEHIND THE RECOMMENDATION").assertDoesNotExist()
    }

    @Test
    fun refreshedRankingKeepsExpansionWithItsActivity() {
        var recommendations by
            mutableStateOf(listOf(surfing, surfing.copy(activity = Activity.SKIING)))
        compose.setContent {
            WeatherPalTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    recommendations.forEachIndexed { index, item ->
                        key(item.activity) { ActivityCard(item, index + 1) }
                    }
                }
            }
        }
        compose.onNodeWithText("Surfing").performClick()
        compose.runOnIdle { recommendations = recommendations.reversed() }
        compose.onNodeWithText("Surfing").assert(state("Expanded"))
        compose.onNodeWithText("Skiing").assert(state("Collapsed"))
    }

    @Test
    fun expansionSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WeatherPalTheme { ActivityCard(surfing, 1) } }
        compose.onNodeWithText("Surfing").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Surfing").assert(state("Expanded"))
        compose.onNodeWithText("BEHIND THE RECOMMENDATION").assertIsDisplayed()
    }

    @Test
    fun browsingDatesKeepsCardStateSeparateAndRestoresItWhenReturning() {
        val date = LocalDate.of(2026, 10, 8)
        val city = City(1, "Lisbon", null, "Portugal", 38.72, -9.13, "Europe/Lisbon")
        val days =
            (0L..6L).map { offset ->
                val day = date.plusDays(offset)
                ForecastDayUi(day, DailyWeather(day, 22.0, 0.0, 10.0, 0.0, 0.0), listOf(surfing))
            }
        var selected by mutableStateOf(date)
        compose.setContent {
            WeatherPalTheme {
                ForecastScreen(
                    ForecastState(city, ForecastContent.Ready(city, days, selected, Instant.EPOCH)),
                    Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC),
                    { selected = it },
                    {},
                )
            }
        }
        fun card(day: LocalDate) =
            compose.onNode(hasText("Surfing") and hasAnyAncestor(hasTestTag("forecast-$day")))
        fun select(day: LocalDate) =
            compose
                .onNodeWithContentDescription(
                    day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                )
                .performClick()

        card(date).performScrollTo().performClick().assert(state("Expanded"))
        select(date.plusDays(1))
        card(date.plusDays(1)).performScrollTo().assert(state("Collapsed"))
        select(date)
        card(date).assert(state("Expanded"))
    }
}
