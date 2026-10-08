package com.example.weatherpal.data

import com.example.weatherpal.data.local.ForecastStore
import com.example.weatherpal.data.remote.*
import com.example.weatherpal.data.repository.CachedWeatherRepository
import com.example.weatherpal.domain.model.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class MemoryStore : ForecastStore {
    val values = MutableStateFlow<Map<Long, ForecastSnapshot>>(emptyMap())
    var commits = 0
    var fail = false

    override suspend fun prune(policy: CachePolicy) {
        values.value =
            values.value.entries
                .sortedWith(
                    compareByDescending<Map.Entry<Long, ForecastSnapshot>> {
                            it.value.lastSuccessfulUpdate
                        }
                        .thenBy { it.key }
                )
                .take(policy.maxCities)
                .associate { it.toPair() }
    }

    override fun cities() =
        values.map { rows ->
            rows.values
                .sortedWith(
                    compareByDescending<ForecastSnapshot> { it.lastSuccessfulUpdate }
                        .thenBy { it.city.id }
                )
                .map { CachedCitySummary(it.city, it.lastSuccessfulUpdate) }
        }

    override fun forecast(id: Long) = values.map { it[id] }

    override suspend fun commit(
        city: City,
        days: Map<LocalDate, DailyWeather>,
        updated: Instant,
        policy: CachePolicy,
    ): Boolean {
        if (fail) error("disk full")
        val old = values.value[city.id]
        val next = values.value.toMutableMap()
        if (old == null && next.size >= policy.maxCities)
            next.remove(
                next.values
                    .sortedWith(
                        compareBy<ForecastSnapshot> { it.lastSuccessfulUpdate }
                            .thenBy { it.city.id }
                    )
                    .first()
                    .city
                    .id
            )
        next[city.id] = ForecastSnapshot(city, days, updated)
        values.value = next
        commits++
        return old?.days != days || old.city != city
    }
}

class MutableClock(
    var now: Instant = Instant.parse("2026-10-07T12:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone() = zone

    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)

    override fun instant() = now
}

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {
    private val city = City(1, "Berlin", null, null, 52.0, 13.0, "Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 7)
    private val days = mapOf(date to DailyWeather(date, 22.0, 0.0, 10.0, 0.0, 0.0))

    @Test
    fun deduplicatesSameCityAndUpdatesUnchangedTimestamp() = runTest {
        val clock = MutableClock()
        val store = MemoryStore()
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
        val store = MemoryStore()
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
        val store = MemoryStore()
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
