package com.example.weatherpal.ui.search

import com.example.weatherpal.domain.model.AppFailure
import com.example.weatherpal.domain.model.CachedCitySummary
import com.example.weatherpal.domain.model.City

sealed interface SearchStatus {
    data object Idle : SearchStatus

    data class Loading(val requestId: Long, val query: String) : SearchStatus

    data class Results(val query: String, val cities: List<City>) : SearchStatus

    data class Empty(val query: String) : SearchStatus

    data class Error(val query: String, val failure: AppFailure) : SearchStatus
}

data class SearchState(
    val query: String = "",
    val status: SearchStatus = SearchStatus.Idle,
    val recent: List<CachedCitySummary> = emptyList(),
    val cacheFailure: AppFailure? = null,
)
