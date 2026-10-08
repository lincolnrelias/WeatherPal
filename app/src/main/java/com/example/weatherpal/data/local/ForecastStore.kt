package com.example.weatherpal.data.local

import com.example.weatherpal.domain.model.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface ForecastStore {
    suspend fun prune(policy: CachePolicy)

    fun cities(): Flow<List<CachedCitySummary>>

    fun forecast(id: Long): Flow<ForecastSnapshot?>

    suspend fun commit(
        city: City,
        days: Map<LocalDate, DailyWeather>,
        updated: Instant,
        policy: CachePolicy,
    ): Boolean
}
