package com.example.weatherpal.domain.repository

import com.example.weatherpal.domain.model.*
import kotlinx.coroutines.flow.Flow

interface CityRepository {
    suspend fun search(query: String): SearchOutcome
}

interface WeatherRepository {
    fun observeCachedCities(): Flow<List<CachedCitySummary>>

    fun observeForecast(cityId: Long): Flow<ForecastSnapshot?>

    suspend fun refresh(city: City): RefreshOutcome
}
