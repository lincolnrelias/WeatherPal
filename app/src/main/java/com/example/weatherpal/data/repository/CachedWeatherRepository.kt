package com.example.weatherpal.data.repository

import com.example.weatherpal.data.local.ForecastStore
import com.example.weatherpal.data.remote.*
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.WeatherRepository
import com.example.weatherpal.domain.scoring.ActivityScorer
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
    private val flights = mutableMapOf<Long, CompletableDeferred<RefreshOutcome>>()

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
        var owner = false
        val flight =
            flightsLock.withLock {
                flights[city.id]
                    ?: CompletableDeferred<RefreshOutcome>().also {
                        flights[city.id] = it
                        owner = true
                    }
            }
        if (!owner) return flight.await()
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
                    val days = remote.fetch(city)
                    days.values.forEach { ActivityScorer.rank(it) }
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
                    RefreshOutcome.Failed(failureOf(e, clock))
                }
            flight.complete(result)
            return result
        } catch (e: CancellationException) {
            flight.cancel(e)
            throw e
        } finally {
            withContext(NonCancellable) {
                flightsLock.withLock { if (flights[city.id] === flight) flights.remove(city.id) }
            }
        }
    }
}
