package com.example.weatherpal.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class City(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    val zone: ZoneId
        get() = ZoneId.of(timezone)
}

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

data class CachePolicy(val maxCities: Int = 3) {
    init {
        require(maxCities >= 1)
    }
}

enum class Activity {
    SKIING,
    SURFING,
    OUTDOOR_SIGHTSEEING,
    INDOOR_SIGHTSEEING,
}

enum class Suitability {
    VERY_FAVORABLE,
    FAVORABLE,
    MIXED,
    UNFAVORABLE,
    INSUFFICIENT_DATA,
}

enum class ReasonCode {
    TEMPERATURE,
    PRECIPITATION,
    WIND,
    SNOW_DEPTH,
    SNOWFALL,
    ZERO_SNOW_CAP,
    INDOOR_TRADEOFF,
    MISSING_INPUTS,
}

enum class SnowSource {
    DEPTH,
    SNOWFALL,
}

data class Reason(val code: ReasonCode, val value: Double? = null, val points: Int? = null)

data class Recommendation(
    val activity: Activity,
    val score: Int?,
    val label: Suitability,
    val reasons: List<Reason>,
    val snowSource: SnowSource? = null,
)

enum class FailureKind {
    VALIDATION,
    NETWORK,
    TIMEOUT,
    HTTP,
    RATE_LIMITED,
    MALFORMED_RESPONSE,
    UNUSABLE_FORECAST,
    STORAGE,
}

data class AppFailure(
    val kind: FailureKind,
    val diagnostic: String? = null,
    val retryAt: Instant? = null,
)

sealed interface SearchOutcome {
    data class Success(val cities: List<City>) : SearchOutcome

    data class Failed(val failure: AppFailure) : SearchOutcome
}

sealed interface RefreshOutcome {
    data class Success(val contentChanged: Boolean) : RefreshOutcome

    data class Failed(val failure: AppFailure) : RefreshOutcome
}
