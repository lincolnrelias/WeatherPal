package com.example.weatherpal.data.repository

import com.example.weatherpal.data.local.ForecastStore
import com.example.weatherpal.data.remote.*
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.RefreshOutcome
import com.example.weatherpal.domain.repository.WeatherRepository
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachedWeatherRepository(
    private val remote: WeatherRemote,
    private val store: ForecastStore,
    private val clock: Clock,
    private val policy: CachePolicy,
) : WeatherRepository {
    private val startup = Mutex()
    private var initialized = false
    private val flightsLock = Mutex()

    private class Flight(val owner: Job) {
        val result = CompletableDeferred<RefreshOutcome>()
    }

    private val flights = mutableMapOf<Long, Flight>()
    private val cooldown = RequestCooldown(clock)

    private suspend fun initialize() =
        startup.withLock {
            if (!initialized) {
                store.prune(policy)
                initialized = true
            }
        }

    override fun observeCachedCities() = flow {
        initialize()
        emitAll(store.cities())
    }

    override fun observeForecast(cityId: Long) = flow {
        initialize()
        emitAll(store.forecast(cityId))
    }

    override suspend fun refresh(city: City): RefreshOutcome {
        while (true) {
            currentCoroutineContext().ensureActive()
            cooldown.currentFailure()?.let {
                return RefreshOutcome.Failed(it)
            }
            var owner = false
            val caller = currentCoroutineContext().job
            val flight =
                flightsLock.withLock {
                    flights[city.id]?.takeIf { it.owner.isActive }
                        ?: Flight(caller).also {
                            flights[city.id] = it
                            owner = true
                        }
                }
            if (owner) return runRefresh(city, flight)
            try {
                return flight.result.await()
            } catch (e: CancellationException) {
                // Only the owner went away. A live waiter must join/start a replacement.
                currentCoroutineContext().ensureActive()
                flightsLock.withLock { if (flights[city.id] === flight) flights.remove(city.id) }
            }
        }
    }

    private suspend fun runRefresh(city: City, flight: Flight): RefreshOutcome {
        try {
            val result =
                try {
                    try {
                        initialize()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw DataFailure(AppFailure(FailureKind.STORAGE, e.message))
                    }
                    cooldown.currentFailure()?.let { throw DataFailure(it) }
                    val days = remote.fetch(city)
                    currentCoroutineContext().ensureActive()
                    val changed =
                        try {
                            store.commit(city, days, clock.instant(), policy)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw DataFailure(AppFailure(FailureKind.STORAGE, e.message))
                        }
                    RefreshOutcome.Success(changed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val failure = failureOf(e, clock)
                    cooldown.record(failure)
                    RefreshOutcome.Failed(failure)
                }
            flight.result.complete(result)
            return result
        } catch (e: CancellationException) {
            flight.result.cancel(e)
            throw e
        } finally {
            withContext(NonCancellable) {
                flightsLock.withLock { if (flights[city.id] === flight) flights.remove(city.id) }
            }
        }
    }
}
