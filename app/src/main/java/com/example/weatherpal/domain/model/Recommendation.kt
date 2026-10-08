package com.example.weatherpal.domain.model

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
