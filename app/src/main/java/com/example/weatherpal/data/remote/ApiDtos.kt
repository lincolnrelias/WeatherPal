package com.example.weatherpal.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class GeocodingDto(val results: List<JsonElement>? = null)

@Serializable
data class CityDto(
    val id: Long? = null,
    val name: String? = null,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
)

@Serializable
data class DailyDto(
    val time: List<String>? = null,
    val temperature_2m_mean: List<JsonElement?>? = null,
    val precipitation_sum: List<JsonElement?>? = null,
    val wind_speed_10m_max: List<JsonElement?>? = null,
    val snowfall_sum: List<JsonElement?>? = null,
)

@Serializable
data class HourlyDto(val time: List<String>? = null, val snow_depth: List<JsonElement?>? = null)

@Serializable
data class ForecastDto(
    val timezone: String? = null,
    val daily: DailyDto? = null,
    val daily_units: Map<String, String>? = null,
    val hourly: JsonElement? = null,
    val hourly_units: JsonElement? = null,
)
