package com.example.weatherpal.ui.forecast

import androidx.lifecycle.*
import com.example.weatherpal.core.AppDispatchers
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.RefreshOutcome
import com.example.weatherpal.domain.repository.WeatherRepository
import com.example.weatherpal.domain.scoring.*
import com.example.weatherpal.domain.weather.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ForecastViewModel(
    private val repository: WeatherRepository,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    saved: SavedStateHandle,
) : ViewModel() {
    private val savedState = ForecastSavedState(saved)
    private val mutable = MutableStateFlow<ForecastState>(ForecastState.Closed)
    val state = mutable.asStateFlow()
    private val current: ForecastState.Open?
        get() = state.value as? ForecastState.Open

    private var collection: Job? = null
    private var refreshJob: Job? = null
    private var midnight: Job? = null
    private var generation = 0L
    private var snapshot: ForecastSnapshot? = null
    private var windowStart: LocalDate? = null
    private var rolloverPending = false

    init {
        savedState.restore()?.let { open(it.city, it.selectedDate) }
    }

    fun open(city: City, restoredDate: LocalDate? = null) {
        generation++
        cancelJobs()
        snapshot = null
        rolloverPending = false
        val window = dateWindow(clock, city.zone)
        windowStart = window.first()
        val selection = restoredDate?.takeIf { it in window } ?: window.first()
        savedState.save(city, selection)
        mutable.value = ForecastState.Open(city, selection)
        observe(city, generation)
        scheduleMidnight()
    }

    private fun update(transform: (ForecastState.Open) -> ForecastState.Open) {
        mutable.update { state -> (state as? ForecastState.Open)?.let(transform) ?: state }
    }

    private fun observe(city: City, identity: Long, refreshOnFirstEmission: Boolean = true) {
        collection?.cancel()
        val job =
            viewModelScope.launch(dispatchers.main, start = CoroutineStart.LAZY) {
                var first = true
                repository
                    .observeForecast(city.id)
                    .catch { e ->
                        if (e is CancellationException) throw e
                        if (identity == generation) fail(AppFailure(FailureKind.STORAGE, e.message))
                    }
                    .collect { value ->
                        currentCoroutineContext().ensureActive()
                        if (identity != generation) return@collect
                        snapshot = value
                        publish()
                        if (first) {
                            first = false
                            if (refreshOnFirstEmission) refresh()
                        }
                    }
            }
        collection = job
        job.start()
    }

    private fun publish() {
        val open = current ?: return
        val data = snapshot ?: return
        val previous = open.content as? ForecastContent.Ready
        val window = dateWindow(clock, open.city.zone)
        val selection = open.selectedDate.takeIf { it in window } ?: window.first()
        savedState.select(selection)
        val days =
            window.map { date ->
                val weather = data.days[date]
                val old = previous?.days?.firstOrNull { it.date == date }
                if (old != null && old.weather == weather) old
                else
                    ForecastDayUi(date, weather, weather?.let { ActivityScorer.rank(it) }.orEmpty())
            }
        update {
            it.copy(
                selectedDate = selection,
                content = ForecastContent.Ready(days, data.lastSuccessfulUpdate),
            )
        }
    }

    fun select(date: LocalDate) {
        val ready = current?.content as? ForecastContent.Ready ?: return
        if (ready.days.none { it.date == date }) return
        savedState.select(date)
        update { it.copy(selectedDate = date) }
    }

    fun refresh() {
        val open = current ?: return
        if (refreshJob?.isActive == true) return
        val failure = (open.refresh as? RefreshStatus.Failed)?.failure
        if (failure?.retryAt?.isAfter(clock.instant()) == true) return
        val identity = generation
        update {
            it.copy(
                refresh = RefreshStatus.Refreshing,
                content =
                    if (it.content is ForecastContent.InitialError) ForecastContent.InitialLoading
                    else it.content,
            )
        }
        if (collection?.isActive != true) observe(open.city, identity, false)
        // Assign before starting so an immediate dispatcher cannot restore an old job reference.
        val job =
            viewModelScope.launch(dispatchers.main, start = CoroutineStart.LAZY) {
                try {
                    val result = repository.refresh(open.city)
                    currentCoroutineContext().ensureActive()
                    if (identity != generation) return@launch
                    when (result) {
                        is RefreshOutcome.Success ->
                            update { it.copy(refresh = RefreshStatus.Idle) }
                        is RefreshOutcome.Failed -> fail(result.failure)
                    }
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    if (identity == generation) fail(AppFailure(FailureKind.CANCELLED))
                } finally {
                    if (identity == generation) {
                        refreshJob = null
                        if (currentCoroutineContext().isActive && rolloverPending) {
                            rolloverPending = false
                            refresh()
                        }
                    }
                }
            }
        refreshJob = job
        job.start()
    }

    private fun fail(failure: AppFailure) {
        update {
            it.copy(
                refresh = RefreshStatus.Failed(failure),
                content =
                    if (it.content is ForecastContent.Ready) it.content
                    else ForecastContent.InitialError(failure),
            )
        }
    }

    fun onResume() {
        reconcileDate()
        scheduleMidnight()
    }

    fun onBackground() {
        midnight?.cancel()
    }

    fun reconcileDate() {
        val open = current ?: return
        val window = dateWindow(clock, open.city.zone)
        val today = window.first()
        if (today != windowStart) {
            windowStart = today
            if (open.selectedDate !in window) {
                savedState.select(today)
                update { it.copy(selectedDate = today) }
            }
            publish()
            if (refreshJob?.isActive == true) rolloverPending = true else refresh()
        }
    }

    private fun scheduleMidnight() {
        midnight?.cancel()
        val city = current?.city ?: return
        midnight =
            viewModelScope.launch(dispatchers.main) {
                while (isActive) {
                    val next =
                        LocalDate.now(clock.withZone(city.zone))
                            .plusDays(1)
                            .atStartOfDay(city.zone)
                            .toInstant()
                    delay(Duration.between(clock.instant(), next).toMillis().coerceAtLeast(1))
                    reconcileDate()
                }
            }
    }

    fun leave() {
        generation++
        cancelJobs()
        snapshot = null
        rolloverPending = false
        savedState.clear()
        mutable.value = ForecastState.Closed
    }

    private fun cancelJobs() {
        collection?.cancel()
        refreshJob?.cancel()
        midnight?.cancel()
        collection = null
        refreshJob = null
        midnight = null
    }
}
