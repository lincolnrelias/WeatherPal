package com.example.weatherpal.ui.search

import androidx.lifecycle.SavedStateHandle
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.CityRepository
import com.example.weatherpal.domain.repository.SearchOutcome
import com.example.weatherpal.testing.ViewModelTestFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest : ViewModelTestFixture() {
    @Test
    fun cacheSpecificRetryRecoversFirstReadAndKeepsReceivingUpdates() = scenario {
        var reads = 0
        var broken = true
        weather.recentFlow = {
            flow {
                reads++
                if (broken) error("transient read failure")
                emitAll(
                    weather.snapshots.map { rows ->
                        rows.values.map { CachedCitySummary(it.city, it.lastSuccessfulUpdate) }
                    }
                )
            }
        }
        var searches = 0
        val vm =
            SearchViewModel(
                    object : CityRepository {
                        override suspend fun search(query: String): SearchOutcome {
                            searches++
                            return SearchOutcome.Success(emptyList())
                        }
                    },
                    weather,
                    clock,
                    dispatchers,
                    SavedStateHandle(),
                )
                .also { viewModels += it }
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
        weather.recentFlow = {
            flow {
                reads++
                if (reads == 1) emit(recent)
                if (reads < 3) error("storage unavailable")
                emit(recent.map { it.copy(lastSuccessfulUpdate = clock.now.plusSeconds(60)) })
            }
        }
        val vm =
            SearchViewModel(
                    object : CityRepository {
                        override suspend fun search(query: String) =
                            SearchOutcome.Success(emptyList())
                    },
                    weather,
                    clock,
                    dispatchers,
                    SavedStateHandle(),
                )
                .also { viewModels += it }
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
