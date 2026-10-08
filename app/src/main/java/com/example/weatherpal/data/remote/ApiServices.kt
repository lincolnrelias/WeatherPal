package com.example.weatherpal.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingService {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingDto
}

interface ForecastService {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("timezone") timezone: String,
        @Query("forecast_days") days: Int = 7,
        @Query("timeformat") timeformat: String = "iso8601",
        @Query("temperature_unit") temperature: String = "celsius",
        @Query("wind_speed_unit") wind: String = "kmh",
        @Query("precipitation_unit") precipitation: String = "mm",
        @Query("daily")
        daily: String = "temperature_2m_mean,precipitation_sum,wind_speed_10m_max,snowfall_sum",
        @Query("hourly") hourly: String = "snow_depth",
    ): ForecastDto
}
