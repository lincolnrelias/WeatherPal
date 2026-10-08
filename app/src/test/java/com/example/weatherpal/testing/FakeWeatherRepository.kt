package com.example.weatherpal.testing

import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.*
import kotlinx.coroutines.flow.*

class FakeWeatherRepository : WeatherRepository {
    val snapshots = MutableStateFlow<Map<Long, ForecastSnapshot>>(emptyMap())
    var calls = 0
    var action: suspend (City) -> RefreshOutcome = { RefreshOutcome.Success(false) }
    var recentFlow: (() -> Flow<List<CachedCitySummary>>)? = null
    var forecastFlow: ((Long) -> Flow<ForecastSnapshot?>)? = null

    override fun observeCachedCities() =
        recentFlow?.invoke()
            ?: snapshots.map {
                it.values.map { row -> CachedCitySummary(row.city, row.lastSuccessfulUpdate) }
            }

    override fun observeForecast(cityId: Long) =
        forecastFlow?.invoke(cityId) ?: snapshots.map { it[cityId] }

    override suspend fun refresh(city: City): RefreshOutcome {
        calls++
        return action(city)
    }
}
