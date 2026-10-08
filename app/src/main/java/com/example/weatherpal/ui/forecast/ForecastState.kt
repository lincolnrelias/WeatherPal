package com.example.weatherpal.ui.forecast

import com.example.weatherpal.domain.model.AppFailure
import com.example.weatherpal.domain.model.City
import com.example.weatherpal.domain.model.DailyWeather
import com.example.weatherpal.domain.model.Recommendation
import java.time.Instant
import java.time.LocalDate

data class ForecastDayUi(
    val date: LocalDate,
    val weather: DailyWeather?,
    val recommendations: List<Recommendation>,
)

sealed interface ForecastContent {
    data object InitialLoading : ForecastContent

    data class InitialError(val failure: AppFailure) : ForecastContent

    data class Ready(val days: List<ForecastDayUi>, val lastSuccessfulUpdate: Instant) :
        ForecastContent
}

sealed interface RefreshStatus {
    data object Idle : RefreshStatus

    data object Refreshing : RefreshStatus

    data class Failed(val failure: AppFailure) : RefreshStatus
}

sealed interface ForecastState {
    data object Closed : ForecastState

    data class Open(
        val city: City,
        val selectedDate: LocalDate,
        val content: ForecastContent = ForecastContent.InitialLoading,
        val refresh: RefreshStatus = RefreshStatus.Idle,
    ) : ForecastState
}
