package com.example.weatherpal.ui.search

import androidx.lifecycle.*
import com.example.weatherpal.core.AppDispatchers
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.*
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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

class SearchViewModel(
    private val cities: CityRepository,
    private val weather: WeatherRepository,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(SearchState(query = saved["query"] ?: ""))
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var requestId = 0L
    private var cachedCities: Job? = null

    init {
        retryCachedCities()
    }

    fun retryCachedCities() {
        if (cachedCities?.isActive == true) return
        cachedCities = viewModelScope.launch(dispatchers.main) {
            weather
                .observeCachedCities()
                .catch { e ->
                    if (e is CancellationException) throw e
                    mutable.update {
                        it.copy(cacheFailure = AppFailure(FailureKind.STORAGE, e.message))
                    }
                }
                .collect { rows -> mutable.update { it.copy(recent = rows, cacheFailure = null) } }
        }
    }

    fun edit(query: String) {
        saved["query"] = query
        mutable.update { it.copy(query = query) }
    }

    fun submit() = submitQuery(state.value.query)

    fun retry() {
        (state.value.status as? SearchStatus.Error)?.let { submitQuery(it.query) }
    }

    private fun submitQuery(raw: String) {
        val query = raw.trim()
        val status = state.value.status
        if (status is SearchStatus.Loading && status.query == query) return
        if (
            status is SearchStatus.Error && status.failure.retryAt?.isAfter(clock.instant()) == true
        )
            return
        request?.cancel()
        val identity = ++requestId
        if (query.codePointCount(0, query.length) < 2) {
            mutable.update {
                it.copy(status = SearchStatus.Error(query, AppFailure(FailureKind.VALIDATION)))
            }
            return
        }
        mutable.update { it.copy(status = SearchStatus.Loading(identity, query)) }
        request =
            viewModelScope.launch(dispatchers.main) {
                val result = cities.search(query)
                currentCoroutineContext().ensureActive()
                if (identity != requestId) return@launch
                mutable.update {
                    it.copy(
                        status =
                            when (result) {
                                is SearchOutcome.Success ->
                                    if (result.cities.isEmpty()) SearchStatus.Empty(query)
                                    else SearchStatus.Results(query, result.cities)
                                is SearchOutcome.Failed -> SearchStatus.Error(query, result.failure)
                            }
                    )
                }
            }
    }

    fun cancelSearch() {
        requestId++
        request?.cancel()
        if (state.value.status is SearchStatus.Loading)
            mutable.update { it.copy(status = SearchStatus.Idle) }
    }

    fun dismissResults() {
        cancelSearch()
        saved["query"] = ""
        mutable.update { it.copy(query = "", status = SearchStatus.Idle) }
    }
}
