package com.example.weatherpal.ui

import androidx.lifecycle.*
import com.example.weatherpal.data.MutableClock
import com.example.weatherpal.data.MemoryStore
import com.example.weatherpal.data.remote.*
import com.example.weatherpal.data.repository.CachedWeatherRepository
import com.example.weatherpal.core.AppDispatchers
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
    var recentFlow: (() -> Flow<List<CachedCitySummary>>)? = null
    var forecastFlow: ((Long) -> Flow<ForecastSnapshot?>)? = null

    override fun observeCachedCities() =
        recentFlow?.invoke() ?: snapshots.map {
            it.values.map { row -> CachedCitySummary(row.city, row.lastSuccessfulUpdate) }
        }

    override fun observeForecast(cityId: Long) = forecastFlow?.invoke(cityId) ?: snapshots.map { it[cityId] }

    override suspend fun refresh(city: City): RefreshOutcome {
        calls++
        return action(city)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = AppDispatchers(dispatcher)
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

    private fun open(vm: ForecastViewModel) = vm.state.value as ForecastState.Open

    private fun ready(vm: ForecastViewModel) = open(vm).content as ForecastContent.Ready

    @Test
    fun immediateDispatcherKeepsOneObserverAndRefreshAndAllowsNextRefresh() = scenario {
        var reads = 0
        weather.forecastFlow = { id -> flow {
            reads++
            emitAll(weather.snapshots.map { it[id] })
        } }
        weather.snapshots.value = mapOf(city.id to snapshot())
        val immediate = AppDispatchers(UnconfinedTestDispatcher(testScheduler))
        val vm = ForecastViewModel(weather, clock, immediate, SavedStateHandle())
            .also { viewModels += it }
        vm.open(city)
        assertEquals(1, reads)
        assertEquals(1, weather.calls)
        assertEquals(RefreshStatus.Idle, open(vm).refresh)
        vm.refresh()
        assertEquals(1, reads)
        assertEquals(2, weather.calls)
    }

    @Test
    fun legacySavedCityMigratesToNamedFieldsAndClosingClearsDestination() = scenario {
        weather.snapshots.value = mapOf(city.id to snapshot())
        val selected = date.plusDays(2)
        val saved = SavedStateHandle(mapOf(
            "city" to arrayListOf("1", "Berlin", "", "Germany", "52.0", "13.0", "Europe/Berlin"),
            "date" to selected.toString(),
        ))
        val vm = vm(saved)
        runCurrent()
        assertEquals(city, open(vm).city)
        assertEquals(selected, open(vm).selectedDate)
        assertEquals("Berlin", saved.get<HashMap<String, String>>("forecast.city")!!["name"])
        assertFalse(saved.contains("city"))
        vm.leave()
        assertEquals(ForecastState.Closed, vm.state.value)
        assertTrue(saved.keys().isEmpty())
    }

    @Test
    fun invalidSavedDateDefaultsToTodayAndInvalidCityStaysClosed() = scenario {
        val valid = SavedStateHandle()
        ForecastSavedState(valid).save(city, date.plusDays(2))
        valid["forecast.date"] = "invalid-date"
        val vm = vm(valid)
        assertEquals(date, open(vm).selectedDate)
        vm.leave()
        val expired = SavedStateHandle()
        ForecastSavedState(expired).save(city, date.minusDays(1))
        assertEquals(date, open(vm(expired)).selectedDate)
        val invalid = SavedStateHandle(mapOf("forecast.city" to hashMapOf("name" to "incomplete")))
        assertEquals(ForecastState.Closed, vm(invalid).state.value)
    }

    @Test
    fun sameCityReentryRecoversBeforeCancelledRemoteCleanupCompletes() = scenario {
        val cleanup = CompletableDeferred<Unit>()
        val store = MemoryStore()
        var calls = 0
        val repo = CachedWeatherRepository(object : WeatherRemote {
            override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                if (++calls == 1) {
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { cleanup.await() } }
                }
                return snapshot().days
            }
        }, store, clock, CachePolicy())
        val vm = ForecastViewModel(repo, clock, dispatchers, SavedStateHandle())
            .also { viewModels += it }
        vm.open(city)
        runCurrent()
        vm.leave()
        vm.open(city)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(7, ready(vm).days.size)
        assertEquals(RefreshStatus.Idle, open(vm).refresh)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(1, store.commits)
        assertEquals(RefreshStatus.Idle, open(vm).refresh)
    }

    @Test
    fun childCancellationWithActiveScreenBecomesRecoverableError() = scenario {
        weather.action = { throw CancellationException("abandoned shared work") }
        val vm = vm()
        vm.open(city)
        runCurrent()
        assertEquals(FailureKind.CANCELLED,
            (open(vm).content as ForecastContent.InitialError).failure.kind)
        assertTrue(open(vm).refresh is RefreshStatus.Failed)
        weather.action = {
            weather.snapshots.value = mapOf(city.id to snapshot())
            RefreshOutcome.Success(true)
        }
        vm.refresh()
        runCurrent()
        assertEquals(7, ready(vm).days.size)
        assertEquals(RefreshStatus.Idle, open(vm).refresh)
    }

    @Test
    fun cacheSpecificRetryRecoversFirstReadAndKeepsReceivingUpdates() = scenario {
        var reads = 0
        var broken = true
        weather.recentFlow = {
            flow {
                reads++
                if (broken) error("transient read failure")
                emitAll(weather.snapshots.map { rows ->
                    rows.values.map { CachedCitySummary(it.city, it.lastSuccessfulUpdate) }
                })
            }
        }
        var searches = 0
        val vm = SearchViewModel(object : CityRepository {
            override suspend fun search(query: String): SearchOutcome {
                searches++
                return SearchOutcome.Success(emptyList())
            }
        }, weather, clock, dispatchers, SavedStateHandle()).also { viewModels += it }
        runCurrent()
        assertEquals(FailureKind.STORAGE, vm.state.value.cacheFailure?.kind)
        vm.retryCachedCities()
        runCurrent()
        assertEquals(2, reads)
        assertEquals(FailureKind.STORAGE, vm.state.value.cacheFailure?.kind)
        broken = false
        weather.snapshots.value = mapOf(city.id to snapshot())
        vm.retryCachedCities()
        vm.retryCachedCities()
        runCurrent()
        assertEquals(3, reads)
        assertEquals(0, searches)
        assertNull(vm.state.value.cacheFailure)
        assertEquals(listOf(city), vm.state.value.recent.map { it.city })
        weather.snapshots.value = mapOf(2L to snapshot().copy(city = city.copy(id = 2)))
        runCurrent()
        assertEquals(listOf(2L), vm.state.value.recent.map { it.city.id })
    }

    @Test
    fun cacheFailureRetainsShortcutsDuringFailedRetryAndThenClears() = scenario {
        val recent = listOf(CachedCitySummary(city, clock.now))
        var reads = 0
        weather.recentFlow = { flow {
            reads++
            if (reads == 1) emit(recent)
            if (reads < 3) error("storage unavailable")
            emit(recent.map { it.copy(lastSuccessfulUpdate = clock.now.plusSeconds(60)) })
        } }
        val vm = SearchViewModel(object : CityRepository {
            override suspend fun search(query: String) = SearchOutcome.Success(emptyList())
        }, weather, clock, dispatchers, SavedStateHandle()).also { viewModels += it }
        runCurrent()
        assertEquals(recent, vm.state.value.recent)
        assertNotNull(vm.state.value.cacheFailure)
        vm.retryCachedCities()
        runCurrent()
        assertEquals(recent, vm.state.value.recent)
        assertNotNull(vm.state.value.cacheFailure)
        vm.retryCachedCities()
        runCurrent()
        assertNull(vm.state.value.cacheFailure)
        assertEquals(clock.now.plusSeconds(60), vm.state.value.recent.single().lastSuccessfulUpdate)
    }

    @Test
    fun forecastObserverRetryResumesCacheUpdatesWithoutDuplicateRefresh() = scenario {
        var reads = 0
        weather.forecastFlow = { id -> flow {
            if (++reads == 1) error("read failed")
            emitAll(weather.snapshots.map { it[id] })
        } }
        val vm = vm()
        vm.open(city)
        runCurrent()
        assertEquals(FailureKind.STORAGE,
            (open(vm).content as ForecastContent.InitialError).failure.kind)
        assertEquals(0, weather.calls)
        weather.action = {
            weather.snapshots.value = mapOf(city.id to snapshot())
            RefreshOutcome.Success(true)
        }
        vm.refresh()
        runCurrent()
        assertEquals(2, reads)
        assertEquals(1, weather.calls)
        assertEquals(7, ready(vm).days.size)
        weather.snapshots.value = mapOf(city.id to snapshot(temp = 15.0))
        runCurrent()
        assertEquals(15.0, ready(vm).days.first().weather!!.temperatureC!!, 0.0)
    }

    @Test
    fun cooldownSurvivesForecastNavigationAndUnlocksAtExactDeadline() = scenario {
        var calls = 0
        val failure = AppFailure(FailureKind.RATE_LIMITED, retryAt = clock.now.plusSeconds(60))
        val repo = CachedWeatherRepository(object : WeatherRemote {
            override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                if (++calls == 1) throw DataFailure(failure)
                return snapshot().days
            }
        }, MemoryStore(), clock, CachePolicy())
        val vm = ForecastViewModel(repo, clock, dispatchers, SavedStateHandle())
            .also { viewModels += it }
        vm.open(city)
        runCurrent()
        vm.leave()
        vm.open(city)
        runCurrent()
        assertEquals(failure, (open(vm).refresh as RefreshStatus.Failed).failure)
        vm.leave()
        vm.open(city.copy(id = 2))
        runCurrent()
        assertEquals(failure, (open(vm).content as ForecastContent.InitialError).failure)
        assertEquals(1, calls)
        clock.now = failure.retryAt!!
        vm.refresh()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(2L, open(vm).city.id)
        assertEquals(RefreshStatus.Idle, open(vm).refresh)
        assertEquals(7, ready(vm).days.size)
    }

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
        assertTrue(open(vm).refresh is RefreshStatus.Failed)
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
        assertEquals(date.plusDays(2), open(vm).selectedDate)
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
        assertEquals(date.plusDays(2), open(vm).selectedDate)
        assertNotEquals(original.days.first(), ready(vm).days.first())
        assertSame(original.days[1], ready(vm).days[1])
    }

    @Test
    fun uncachedFailureRetryAndCancellationOnNavigation() = scenario {
        weather.action = { RefreshOutcome.Failed(AppFailure(FailureKind.NETWORK)) }
        val vm = vm()
        vm.open(city)
        runCurrent()
        assertTrue(open(vm).content is ForecastContent.InitialError)
        val gate = CompletableDeferred<Unit>()
        weather.action = {
            withContext(NonCancellable) { gate.await() }
            RefreshOutcome.Failed(AppFailure(FailureKind.TIMEOUT))
        }
        vm.refresh()
        runCurrent()
        assertTrue(open(vm).refresh is RefreshStatus.Refreshing)
        vm.leave()
        gate.complete(Unit)
        runCurrent()
        assertEquals(ForecastState.Closed, vm.state.value)
        weather.action = {
            weather.snapshots.value = mapOf(1L to snapshot())
            RefreshOutcome.Success(true)
        }
        vm.open(city)
        runCurrent()
        assertTrue(open(vm).content is ForecastContent.Ready)
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
        assertEquals(date.plusDays(3), open(vm).selectedDate)
        assertEquals(2, weather.calls)
        vm.onResume()
        runCurrent()
        assertEquals(2, weather.calls)
        clock.now = Instant.parse("2026-10-20T10:00:00Z")
        vm.onResume()
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 20), open(vm).selectedDate)
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
        assertEquals(date.plusDays(2), open(vm).selectedDate)
        val savedCopy =
            SavedStateHandle(
                mapOf(
                    "forecast.city" to saved.get<HashMap<String, String>>("forecast.city"),
                    "forecast.date" to saved.get<String>("forecast.date"),
                )
            )
        vm.leave()
        val restored = vm(savedCopy)
        runCurrent()
        assertEquals(city, open(restored).city)
        assertEquals(date.plusDays(2), open(restored).selectedDate)
        assertEquals(ForecastState.Closed, vm().state.value)
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
