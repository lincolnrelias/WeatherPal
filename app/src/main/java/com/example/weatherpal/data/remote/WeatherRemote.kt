package com.example.weatherpal.data.remote

import com.example.weatherpal.domain.model.*
import java.time.Clock
import java.time.LocalDate

interface WeatherRemote {
    suspend fun fetch(city: City): Map<LocalDate, DailyWeather>
}

class OpenMeteoRemote(private val service: ForecastService, private val clock: Clock) :
    WeatherRemote {
    override suspend fun fetch(city: City) =
        mapForecast(service.forecast(city.latitude, city.longitude, city.timezone), city, clock)
}
