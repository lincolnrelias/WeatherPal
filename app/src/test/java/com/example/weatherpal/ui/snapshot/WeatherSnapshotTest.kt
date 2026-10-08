package com.example.weatherpal.ui.snapshot

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dropbox.differ.SimpleImageComparator
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.scoring.ActivityScorer
import com.example.weatherpal.ui.forecast.*
import com.example.weatherpal.ui.search.*
import com.example.weatherpal.ui.theme.WeatherPalTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.*
import java.util.Locale
import java.util.TimeZone
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render real screens with immutable fixtures, without starting networking or Room. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherSnapshotTest(private val theme: String) {
    @get:Rule val compose = createComposeRule()

    private val originalLocale = Locale.getDefault()
    private val originalTimeZone = TimeZone.getDefault()
    private val today = LocalDate.of(2026, 10, 8)
    private val updated = Instant.parse("2026-10-08T11:30:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val lisbon = City(2267057, "Lisbon", "Lisbon", "Portugal", 38.72, -9.13, "Europe/Lisbon")
    private val porto = City(2735943, "Porto", "Porto", "Portugal", 41.15, -8.61, "Europe/Lisbon")
    private val networkFailure = AppFailure(FailureKind.NETWORK)
    private val days = (0L..6L).map { offset ->
        val date = today.plusDays(offset)
        val weather = DailyWeather(date, 22.0 + offset, 0.0, 10.0, 0.0, 0.0)
        ForecastDayUi(date, weather, ActivityScorer.rank(weather))
    }
    private val ready = ForecastContent.Ready(days, updated)

    companion object {
        @JvmStatic @Parameters(name = "{0}")
        fun themes() = listOf(arrayOf("light"), arrayOf("dark"))

        @JvmStatic @BeforeClass
        fun requireSnapshotTask() {
            // Ordinary unit tests stay fast and platform independent. Roborazzi's tasks set
            // these properties; invoking a snapshot test without a mode explicitly skips it.
            Assume.assumeTrue(
                "Run :app:verifyRoborazziDebug, :app:compareRoborazziDebug, or :app:recordRoborazziDebug",
                listOf("record", "compare", "verify").any {
                    System.getProperty("roborazzi.test.$it").toBoolean()
                },
            )
        }
    }

    @Before
    fun configureRendering() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        qualifiers()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreEnvironment() {
        RuntimeEnvironment.setFontScale(1f)
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    private fun qualifiers(viewport: String = "w411dp-h891dp") {
        val night = if (theme == "dark") "night" else "notnight"
        RuntimeEnvironment.setQualifiers("en-rUS-$viewport-$night-mdpi")
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            WeatherPalTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        settle()
    }

    private fun settle() {
        // Advance a known amount rather than depending on wall time or the host's frame rate.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(
            "$name-$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = 0f,
                    imageComparator = SimpleImageComparator(maxDistance = 0f),
                ),
            ),
        )
    }

    private fun search(state: SearchState) {
        render { SearchScreen(state, clock, {}, {}, {}, {}, {}) }
    }

    private fun forecast(
        content: ForecastContent = ready,
        refresh: RefreshStatus = RefreshStatus.Idle,
    ) {
        render { ForecastScreen(ForecastState.Open(lisbon, today, content, refresh), clock, {}, {}) }
    }

    @Test fun searchWelcome() {
        search(SearchState())
        capture("search-welcome")
    }

    @Test fun searchRecentPlaces() {
        search(SearchState(recent = listOf(CachedCitySummary(lisbon, updated), CachedCitySummary(porto, updated))))
        // Include the saved-city rows below the welcome photograph.
        compose.onNodeWithText("Your recent places").performScrollTo()
        settle()
        capture("search-recent-places")
    }

    @Test fun searchResults() {
        search(SearchState("Lis", SearchStatus.Results("Lis", listOf(lisbon, porto))))
        capture("search-results")
    }

    @Test fun searchNoResults() {
        search(SearchState("Atlantis", SearchStatus.Empty("Atlantis")))
        capture("search-no-results")
    }

    @Test fun searchLoading() {
        search(SearchState("Lisbon", SearchStatus.Loading(1, "Lisbon")))
        capture("search-loading")
    }

    @Test fun searchNetworkError() {
        search(SearchState("Lisbon", SearchStatus.Error("Lisbon", networkFailure)))
        capture("search-network-error")
    }

    @Test fun searchRateLimited() {
        val failure = AppFailure(FailureKind.RATE_LIMITED, retryAt = clock.instant().plusSeconds(30))
        search(SearchState("Lisbon", SearchStatus.Error("Lisbon", failure)))
        capture("search-rate-limited")
    }

    @Test fun searchValidationError() {
        search(SearchState("L", SearchStatus.Error("L", AppFailure(FailureKind.VALIDATION))))
        capture("search-validation-error")
    }

    @Test fun forecastLoading() {
        forecast(ForecastContent.InitialLoading)
        capture("forecast-loading")
    }

    @Test fun forecastInitialError() {
        forecast(ForecastContent.InitialError(networkFailure))
        capture("forecast-initial-error")
    }

    @Test fun forecastReady() {
        forecast()
        capture("forecast-ready")
    }

    @Test fun forecastOffline() {
        forecast(refresh = RefreshStatus.Failed(networkFailure))
        capture("forecast-offline")
    }

    @Test fun forecastMissingDay() {
        forecast(ready.copy(days = listOf(ForecastDayUi(today, null, emptyList())) + days.drop(1)))
        capture("forecast-missing-day")
    }

    @Test fun forecastLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        forecast()
        capture("forecast-large-text")
    }

    @Test fun forecastLandscape() {
        qualifiers("w891dp-h411dp-land")
        forecast()
        capture("forecast-landscape")
    }

    @Test fun activityCardsCollapsed() {
        val labels = listOf(Suitability.VERY_FAVORABLE, Suitability.MIXED, Suitability.UNFAVORABLE, Suitability.INSUFFICIENT_DATA)
        render {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Activity.entries.forEachIndexed { index, activity ->
                    ActivityCard(
                        Recommendation(activity, null, labels[index], listOf(Reason(ReasonCode.MISSING_INPUTS))),
                        index + 1,
                    )
                }
            }
        }
        capture("activity-cards-collapsed")
    }

    @Test fun activityCardExpanded() {
        val surfing = days.first().recommendations.first { it.activity == Activity.SURFING }
        render { Column(Modifier.padding(20.dp)) { ActivityCard(surfing, 1) } }
        compose.onNodeWithText("Surfing").performClick()
        settle()
        compose.onNodeWithText("BEHIND THE RECOMMENDATION").assertIsDisplayed()
        capture("activity-card-expanded")
    }
}
