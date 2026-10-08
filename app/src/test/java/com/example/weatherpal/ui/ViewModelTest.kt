package com.example.weatherpal.ui

import androidx.lifecycle.*
import com.example.weatherpal.data.MutableClock
import com.example.weatherpal.di.AppDispatchers
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.*
import com.example.weatherpal.ui.forecast.*
import com.example.weatherpal.ui.search.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

private class FakeWeather : WeatherRepository {
    val snapshots = MutableStateFlow<Map<Long, ForecastSnapshot>>(emptyMap())
    var calls = 0
    var action: suspend (City) -> RefreshOutcome = { RefreshOutcome.Success(false) }

    override fun observeCachedCities() =
        snapshots.map {
            it.values.map { row -> CachedCitySummary(row.city, row.lastSuccessfulUpdate) }
        }

    override fun observeForecast(cityId: Long) = snapshots.map { it[cityId] }

    override suspend fun refresh(city: City): RefreshOutcome {
        calls++
        return action(city)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
    private val clock = MutableClock()
    private val city = City(1, "Berlin", null, "Germany", 52.0, 13.0, "Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 7)
    private val weather = FakeWeather()
    private val viewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        viewModels.forEach {
            ViewModelStore().apply {
                put("vm", it)
                clear()
            }
        }
        Dispatchers.resetMain()
    }

    private fun scenario(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                viewModels.forEach {
                    ViewModelStore().apply {
                        put("vm", it)
                        clear()
                    }
                }
            }
        }

    private fun snapshot(temp: Double = 22.0, time: Instant = clock.now) =
        ForecastSnapshot(
            city,
            (0..6).associate { i ->
                val d = date.plusDays(i.toLong())
                d to DailyWeather(d, temp, 0.0, 10.0, 0.0, 0.0)
            },
            time,
        )

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) =
        ForecastViewModel(weather, clock, dispatchers, saved).also { viewModels += it }

    private fun ready(vm: ForecastViewModel) = vm.state.value.content as ForecastContent.Ready

    @Test
    fun searchIsSubmitOnlyDeduplicatesAndRejectsStaleResults() = scenario {
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val queries = mutableListOf<String>()
        val cities =
            object : CityRepository {
                override suspend fun search(query: String): SearchOutcome {
                    queries += query
                    withContext(NonCancellable) {
                        gates.getOrPut(query) { CompletableDeferred() }.await()
                    }
                    return SearchOutcome.Success(listOf(city.copy(name = query)))
                }
            }
        val vm =
            SearchViewModel(cities, weather, clock, dispatchers, SavedStateHandle()).also {
                viewModels += it
            }
        vm.edit(" B ")
        runCurrent()
        assertTrue(queries.isEmpty())
        vm.submit()
        runCurrent()
        assertTrue(queries.isEmpty())
        vm.edit(" Berlin ")
        vm.submit()
        runCurrent()
        vm.submit()
        runCurrent()
        assertEquals(listOf("Berlin"), queries)
        vm.edit("Paris")
        runCurrent()
        assertEquals(listOf("Berlin"), queries)
        vm.submit()
        runCurrent()
        gates["Paris"]!!.complete(Unit)
        runCurrent()
        assertEquals("Paris", (vm.state.value.status as SearchStatus.Results).query)
        gates["Berlin"]!!.complete(Unit)
        runCurrent()
        assertEquals("Paris", (vm.state.value.status as SearchStatus.Results).query)
        vm.submit()
        runCurrent()
        assertEquals(3, queries.size)
    }

    @Test
    fun dismissingSearchResultsReturnsToStartAndKeepsRecentPlaces() = scenario {
        weather.snapshots.value = mapOf(city.id to snapshot())
        val saved = SavedStateHandle()
        val vm =
            SearchViewModel(
                    object : CityRepository {
                        override suspend fun search(query: String) =
                            SearchOutcome.Success(listOf(city))
                    },
                    weather,
                    clock,
                    dispatchers,
                    saved,
                )
                .also { viewModels += it }
        vm.edit("Berlin")
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.status is SearchStatus.Results)
        val recent = vm.state.value.recent
        assertEquals(listOf(city), recent.map { it.city })

        vm.dismissResults()

        assertEquals(SearchStatus.Idle, vm.state.value.status)
        assertEquals("", vm.state.value.query)
        assertEquals("", saved.get<String>("query"))
        assertEquals(recent, vm.state.value.recent)
    }

    @Test
    fun searchRetryUsesSubmittedTextAndRateLimitBlocksRequests() = scenario {
        val queries = mutableListOf<String>()
        var failure = AppFailure(FailureKind.NETWORK)
        val vm =
            SearchViewModel(
                    object : CityRepository {
                        override suspend fun search(query: String): SearchOutcome {
                            queries += query
                            return SearchOutcome.Failed(failure)
                        }
                    },
                    weather,
                    clock,
                    dispatchers,
                    SavedStateHandle(),
                )
                .also { viewModels += it }
        vm.edit("Berlin")
        vm.submit()
        runCurrent()
        vm.edit("Paris")
        vm.retry()
        runCurrent()
        assertEquals(listOf("Berlin", "Berlin"), queries)
        failure = AppFailure(FailureKind.RATE_LIMITED, retryAt = clock.now.plusSeconds(60))
        vm.submit()
        runCurrent()
        vm.retry()
        vm.submit()
        runCurrent()
        assertEquals(3, queries.size)
        clock.now = clock.now.plusSeconds(60)
        vm.retry()
        runCurrent()
        assertEquals(4, queries.size)
    }

    @Test
    fun cachedContentRefreshDedupAndFailurePreservation() = scenario {
        weather.snapshots.value = mapOf(1L to snapshot())
        val gate = CompletableDeferred<Unit>()
        weather.action = {
            gate.await()
            RefreshOutcome.Failed(AppFailure(FailureKind.NETWORK))
        }
        val vm = vm()
        vm.open(city)
        runCurrent()
        assertEquals(7, ready(vm).days.size)
        val future = date.plusDays(3)
        vm.select(future)
        vm.refresh()
        vm.refresh()
        runCurrent()
        assertEquals(1, weather.calls)
        val before = ready(vm)
        gate.complete(Unit)
        runCurrent()
        assertEquals(before, ready(vm))
        assertTrue(vm.state.value.refresh is RefreshStatus.Failed)
    }

    @Test
    fun changedAndUnchangedRefreshPreserveDateAndEqualDayObjects() = scenario {
        weather.snapshots.value = mapOf(1L to snapshot())
        val vm = vm()
        vm.open(city)
        runCurrent()
        vm.select(date.plusDays(2))
        val original = ready(vm)
        clock.now = clock.now.plusSeconds(60)
        weather.action = {
            weather.snapshots.value = mapOf(1L to snapshot(time = clock.now))
            RefreshOutcome.Success(false)
        }
        vm.refresh()
        runCurrent()
        assertEquals(date.plusDays(2), ready(vm).selectedDate)
        assertEquals(clock.now, ready(vm).lastSuccessfulUpdate)
        original.days.zip(ready(vm).days).forEach { (a, b) -> assertSame(a, b) }
        val changed =
            snapshot().let {
                it.copy(days = it.days + (date to it.days[date]!!.copy(temperatureC = 10.0)))
            }
        weather.action = {
            weather.snapshots.value = mapOf(1L to changed)
            RefreshOutcome.Success(true)
        }
        vm.refresh()
        runCurrent()
        assertEquals(date.plusDays(2), ready(vm).selectedDate)
        assertNotEquals(original.days.first(), ready(vm).days.first())
        assertSame(original.days[1], ready(vm).days[1])
    }

    @Test
    fun uncachedFailureRetryAndCancellationOnNavigation() = scenario {
        weather.action = { RefreshOutcome.Failed(AppFailure(FailureKind.NETWORK)) }
        val vm = vm()
        vm.open(city)
        runCurrent()
        assertTrue(vm.state.value.content is ForecastContent.InitialError)
        val gate = CompletableDeferred<Unit>()
        weather.action = {
            withContext(NonCancellable) { gate.await() }
            RefreshOutcome.Failed(AppFailure(FailureKind.TIMEOUT))
        }
        vm.refresh()
        runCurrent()
        assertTrue(vm.state.value.refresh is RefreshStatus.Refreshing)
        vm.leave()
        gate.complete(Unit)
        runCurrent()
        assertNull(vm.state.value.city)
        assertNull(vm.state.value.content)
        weather.action = {
            weather.snapshots.value = mapOf(1L to snapshot())
            RefreshOutcome.Success(true)
        }
        vm.open(city)
        runCurrent()
        assertTrue(vm.state.value.content is ForecastContent.Ready)
    }

    @Test
    fun resumeAndMidnightRolloverRetainValidDateOrChooseToday() = scenario {
        weather.snapshots.value = mapOf(1L to snapshot())
        val lastSuccessfulUpdate = clock.now
        val vm = vm()
        vm.open(city)
        runCurrent()
        vm.select(date.plusDays(3))
        clock.now = Instant.parse("2026-10-08T10:00:00Z")
        vm.onResume()
        runCurrent()
        assertEquals(date.plusDays(3), ready(vm).selectedDate)
        assertEquals(2, weather.calls)
        vm.onResume()
        runCurrent()
        assertEquals(2, weather.calls)
        clock.now = Instant.parse("2026-10-20T10:00:00Z")
        vm.onResume()
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 20), ready(vm).selectedDate)
        assertTrue(ready(vm).days.all { it.weather == null })
        assertEquals(lastSuccessfulUpdate, ready(vm).lastSuccessfulUpdate)
        vm.leave()
    }

    @Test
    fun timerReconcilesWhileVisibleAndRestoresCityDate() = scenario {
        clock.now = Instant.parse("2026-10-07T21:59:59Z")
        weather.snapshots.value = mapOf(1L to snapshot())
        val saved = SavedStateHandle()
        val vm = vm(saved)
        vm.open(city)
        runCurrent()
        vm.select(date.plusDays(2))
        clock.now = clock.now.plusSeconds(2)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(date.plusDays(1), ready(vm).days.first().date)
        assertEquals(date.plusDays(2), ready(vm).selectedDate)
        val savedCopy =
            SavedStateHandle(
                mapOf(
                    "city" to saved.get<ArrayList<String>>("city"),
                    "date" to saved.get<String>("date"),
                )
            )
        vm.leave()
        val restored = vm(savedCopy)
        runCurrent()
        assertEquals(city, restored.state.value.city)
        assertEquals(date.plusDays(2), ready(restored).selectedDate)
        assertNull(vm().state.value.city)
    }

    @Test
    fun rolloverDuringRefreshQueuesExactlyOneNewWindowRefresh() = scenario {
        weather.snapshots.value = mapOf(1L to snapshot())
        val gate = CompletableDeferred<Unit>()
        weather.action = {
            gate.await()
            RefreshOutcome.Success(false)
        }
        val vm = vm()
        vm.open(city)
        runCurrent()
        assertEquals(1, weather.calls)
        clock.now = clock.now.plusSeconds(86400)
        vm.onResume()
        vm.onResume()
        runCurrent()
        assertEquals(1, weather.calls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, weather.calls)
        vm.onResume()
        runCurrent()
        assertEquals(2, weather.calls)
    }

    @Test
    fun unicodeValidationEmptyMatchesAndFailureKeepCachedShortcuts() = scenario {
        weather.snapshots.value = mapOf(1L to snapshot())
        var calls = 0
        var outcome: SearchOutcome = SearchOutcome.Success(emptyList())
        val vm =
            SearchViewModel(
                    object : CityRepository {
                        override suspend fun search(query: String): SearchOutcome {
                            calls++
                            return outcome
                        }
                    },
                    weather,
                    clock,
                    dispatchers,
                    SavedStateHandle(),
                )
                .also { viewModels += it }
        vm.edit("🌤")
        vm.submit()
        runCurrent()
        assertEquals(0, calls)
        assertEquals(
            FailureKind.VALIDATION,
            (vm.state.value.status as SearchStatus.Error).failure.kind,
        )
        vm.edit("🌤🌤")
        vm.submit()
        runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.state.value.status is SearchStatus.Empty)
        assertEquals(1, vm.state.value.recent.size)
        outcome = SearchOutcome.Failed(AppFailure(FailureKind.NETWORK))
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.status is SearchStatus.Error)
        assertEquals(1, vm.state.value.recent.size)
    }
}
