package com.example.weatherpal.data.repository

import com.example.weatherpal.data.remote.*
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.RefreshOutcome
import com.example.weatherpal.testing.InMemoryForecastStore
import com.example.weatherpal.testing.MutableClock
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachedWeatherRepositoryTest {
    private val city = City(1, "Berlin", null, null, 52.0, 13.0, "Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 7)
    private val days = mapOf(date to DailyWeather(date, 22.0, 0.0, 10.0, 0.0, 0.0))

    @Test
    fun liveWaiterReplacesCancelledOwnerAfterDelayedCleanup() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val store = InMemoryForecastStore()
        var calls = 0
        val repo =
            CachedWeatherRepository(
                object : WeatherRemote {
                    override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                        if (++calls == 1) {
                            try {
                                awaitCancellation()
                            } finally {
                                withContext(NonCancellable) { cleanup.await() }
                            }
                        }
                        return days
                    }
                },
                store,
                MutableClock(),
                CachePolicy(),
            )
        val owner = async { repo.refresh(city) }
        runCurrent()
        val waiter = async { repo.refresh(city) }
        runCurrent()
        owner.cancel()
        runCurrent()
        assertFalse(waiter.isCompleted)
        cleanup.complete(Unit)
        assertEquals(RefreshOutcome.Success(true), waiter.await())
        owner.join()
        assertTrue(owner.isCancelled)
        assertEquals(2, calls)
        assertEquals(1, store.commits)
    }

    @Test
    fun reentryStartsReplacementBeforeOldCleanupAndOldOwnerCannotRemoveIt() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val store = InMemoryForecastStore()
        var calls = 0
        val repo =
            CachedWeatherRepository(
                object : WeatherRemote {
                    override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                        if (++calls == 1) {
                            try {
                                awaitCancellation()
                            } finally {
                                withContext(NonCancellable) { cleanup.await() }
                            }
                        }
                        response.await()
                        return days
                    }
                },
                store,
                MutableClock(),
                CachePolicy(),
            )
        val owner = async { repo.refresh(city) }
        runCurrent()
        owner.cancel()
        runCurrent()
        val replacement = async { repo.refresh(city) }
        runCurrent()
        assertEquals(2, calls)
        cleanup.complete(Unit)
        owner.join()
        val waiter = async { repo.refresh(city) }
        runCurrent()
        assertEquals(2, calls)
        response.complete(Unit)
        assertEquals(replacement.await(), waiter.await())
        assertEquals(1, store.commits)
    }

    @Test
    fun cancellingOnlyWaiterLeavesOwnerAndOtherWaitersRunning() = runTest {
        val response = CompletableDeferred<Unit>()
        val store = InMemoryForecastStore()
        var calls = 0
        val repo =
            CachedWeatherRepository(
                object : WeatherRemote {
                    override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                        calls++
                        response.await()
                        return days
                    }
                },
                store,
                MutableClock(),
                CachePolicy(),
            )
        val owner = async { repo.refresh(city) }
        runCurrent()
        val waiter = async { repo.refresh(city) }
        val other = async { repo.refresh(city) }
        runCurrent()
        waiter.cancelAndJoin()
        response.complete(Unit)
        assertEquals(RefreshOutcome.Success(true), owner.await())
        assertEquals(owner.await(), other.await())
        assertEquals(1, calls)
        assertEquals(1, store.commits)
    }

    @Test
    fun providerCooldownBlocksEveryCityUntilExactDeadlineAndPreservesCache() = runTest {
        val clock = MutableClock()
        val store = InMemoryForecastStore()
        store.commit(city, days, clock.now, CachePolicy())
        val cached = store.values.value
        val failure = AppFailure(FailureKind.RATE_LIMITED, retryAt = clock.now.plusSeconds(60))
        var calls = 0
        val repo =
            CachedWeatherRepository(
                object : WeatherRemote {
                    override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                        if (++calls == 1) throw DataFailure(failure)
                        return days
                    }
                },
                store,
                clock,
                CachePolicy(),
            )
        assertEquals(RefreshOutcome.Failed(failure), repo.refresh(city))
        assertEquals(RefreshOutcome.Failed(failure), repo.refresh(city))
        assertEquals(RefreshOutcome.Failed(failure), repo.refresh(city.copy(id = 2)))
        clock.now = failure.retryAt!!.minusNanos(1)
        assertEquals(RefreshOutcome.Failed(failure), repo.refresh(city.copy(id = 2)))
        assertEquals(1, calls)
        assertEquals(cached, store.values.value)
        clock.now = failure.retryAt
        assertEquals(RefreshOutcome.Success(true), repo.refresh(city.copy(id = 2)))
        assertEquals(2, calls)
        assertEquals(2, store.commits)
    }

    @Test
    fun deduplicatesSameCityAndUpdatesUnchangedTimestamp() = runTest {
        val clock = MutableClock()
        val store = InMemoryForecastStore()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val remote =
            object : WeatherRemote {
                override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                    calls++
                    gate.await()
                    return days
                }
            }
        val repo = CachedWeatherRepository(remote, store, clock, CachePolicy(1))
        val first = async { repo.refresh(city) }
        runCurrent()
        val second = async { repo.refresh(city) }
        runCurrent()
        assertEquals(1, calls)
        gate.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, store.commits)
        clock.now = clock.now.plusSeconds(60)
        assertEquals(RefreshOutcome.Success(false), repo.refresh(city))
        assertEquals(clock.now, repo.observeForecast(1).first()!!.lastSuccessfulUpdate)
    }

    @Test
    fun networkValidationAndStorageFailuresPreserveCache() = runTest {
        val store = InMemoryForecastStore()
        val clock = MutableClock()
        store.commit(city, days, clock.now, CachePolicy(1))
        val before = store.values.value
        for (failure in
            listOf(
                java.io.IOException("offline"),
                DataFailure(AppFailure(FailureKind.UNUSABLE_FORECAST)),
            )) {
            val remote =
                object : WeatherRemote {
                    override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                        throw failure
                    }
                }
            val repo = CachedWeatherRepository(remote, store, clock, CachePolicy(1))
            assertTrue(repo.refresh(city.copy(id = 2)) is RefreshOutcome.Failed)
            assertEquals(before, store.values.value)
        }
        store.fail = true
        val repo =
            CachedWeatherRepository(
                object : WeatherRemote {
                    override suspend fun fetch(city: City) = days
                },
                store,
                clock,
                CachePolicy(1),
            )
        assertEquals(
            FailureKind.STORAGE,
            (repo.refresh(city.copy(id = 2)) as RefreshOutcome.Failed).failure.kind,
        )
        assertEquals(before, store.values.value)
    }

    @Test
    fun cancelledLateResponseCannotCommitAndCanRetry() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = InMemoryForecastStore()
        var calls = 0
        val repo =
            CachedWeatherRepository(
                object : WeatherRemote {
                    override suspend fun fetch(city: City): Map<LocalDate, DailyWeather> {
                        calls++
                        withContext(NonCancellable) { gate.await() }
                        return days
                    }
                },
                store,
                MutableClock(),
                CachePolicy(),
            )
        val job = launch { repo.refresh(city) }
        runCurrent()
        job.cancel()
        gate.complete(Unit)
        job.join()
        assertEquals(0, store.commits)
        assertTrue(repo.refresh(city) is RefreshOutcome.Success)
        assertEquals(2, calls)
    }
}
