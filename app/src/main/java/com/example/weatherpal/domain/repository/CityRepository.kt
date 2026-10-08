package com.example.weatherpal.domain.repository

import com.example.weatherpal.domain.model.AppFailure
import com.example.weatherpal.domain.model.City

interface CityRepository {
    suspend fun search(query: String): SearchOutcome
}

sealed interface SearchOutcome {
    data class Success(val cities: List<City>) : SearchOutcome

    data class Failed(val failure: AppFailure) : SearchOutcome
}
