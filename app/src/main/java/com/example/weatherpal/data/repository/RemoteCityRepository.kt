package com.example.weatherpal.data.repository

import com.example.weatherpal.data.remote.*
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.CityRepository
import java.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class RemoteCityRepository(private val service: GeocodingService, private val clock: Clock) :
    CityRepository {
    private val cooldown = RequestCooldown(clock)

    override suspend fun search(query: String): SearchOutcome {
        currentCoroutineContext().ensureActive()
        cooldown.currentFailure()?.let { return SearchOutcome.Failed(it) }
        return try {
            SearchOutcome.Success(mapCities(service.search(query.trim())))
        } catch (e: Exception) {
            val failure = failureOf(e, clock)
            cooldown.record(failure)
            SearchOutcome.Failed(failure)
        }
    }
}
