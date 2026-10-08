package com.example.weatherpal.domain.repository

import com.example.weatherpal.domain.model.AppFailure
import com.example.weatherpal.domain.model.CachedCitySummary
import com.example.weatherpal.domain.model.City
import com.example.weatherpal.domain.model.ForecastSnapshot
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun observeCachedCities(): Flow<List<CachedCitySummary>>

    fun observeForecast(cityId: Long): Flow<ForecastSnapshot?>

    suspend fun refresh(city: City): RefreshOutcome
}

sealed interface RefreshOutcome {
    data class Success(val contentChanged: Boolean) : RefreshOutcome

    data class Failed(val failure: AppFailure) : RefreshOutcome
}
