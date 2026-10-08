package com.example.weatherpal.domain.model

import java.time.Instant
import java.time.LocalDate

data class DailyWeather(
    val date: LocalDate,
    val temperatureC: Double?,
    val precipitationMm: Double?,
    val windKmh: Double?,
    val snowDepthCm: Double?,
    val snowfallCm: Double?,
    val depthValidSamples: Int = 0,
    val depthExpectedSamples: Int = 24,
)

data class ForecastSnapshot(
    val city: City,
    val days: Map<LocalDate, DailyWeather>,
    val lastSuccessfulUpdate: Instant,
)

data class CachedCitySummary(val city: City, val lastSuccessfulUpdate: Instant)
