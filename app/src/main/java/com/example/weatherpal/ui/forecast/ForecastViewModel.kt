package com.example.weatherpal.ui.forecast

import androidx.lifecycle.*
import com.example.weatherpal.di.AppDispatchers
import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.WeatherRepository
import com.example.weatherpal.domain.scoring.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ForecastDayUi(val date:LocalDate,val weather:DailyWeather?,val recommendations:List<Recommendation>)
sealed interface ForecastContent {
    data class InitialLoading(val city:City):ForecastContent
    data class InitialError(val city:City,val failure:AppFailure):ForecastContent
    data class Ready(val city:City,val days:List<ForecastDayUi>,val selectedDate:LocalDate,val lastSuccessfulUpdate:Instant):ForecastContent
}
sealed interface RefreshStatus {
    data object Idle:RefreshStatus
    data object Refreshing:RefreshStatus
    data class Failed(val failure:AppFailure):RefreshStatus
}
data class ForecastState(val city:City?=null,val content:ForecastContent?=null,val refresh:RefreshStatus=RefreshStatus.Idle)

class ForecastViewModel(private val repository:WeatherRepository,private val clock:Clock,
    private val dispatchers:AppDispatchers,private val saved:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(ForecastState())
    val state=mutable.asStateFlow()
    private var collection:Job?=null;private var refreshJob:Job?=null;private var midnight:Job?=null
    private var generation=0L
    private var snapshot:ForecastSnapshot?=null
    private var selection:LocalDate?=null
    private var windowStart:LocalDate?=null
    init {
        val fields=saved.get<ArrayList<String>>("city")
        if(fields!=null) runCatching {
            val city=City(fields[0].toLong(),fields[1],fields[2].ifEmpty{null},fields[3].ifEmpty{null},fields[4].toDouble(),fields[5].toDouble(),fields[6])
            val date=saved.get<String>("date")?.let{LocalDate.parse(it)}
            open(city,date)
        }
    }
    fun open(city:City,restoredDate:LocalDate?=null) {
        cancelJobs();generation++;snapshot=null
        val window=dateWindow(clock,city.zone);windowStart=window.first()
        selection=restoredDate?.takeIf{it in window}?:window.first()
        saved["city"]=arrayListOf(city.id.toString(),city.name,city.region?:"",city.country?:"",city.latitude.toString(),city.longitude.toString(),city.timezone)
        saved["date"]=selection.toString()
        mutable.value=ForecastState(city,ForecastContent.InitialLoading(city))
        observe(city,generation);scheduleMidnight()
    }
    private fun observe(city:City,identity:Long) {
        collection?.cancel()
        collection=viewModelScope.launch(dispatchers.main) {
            var first=true
            repository.observeForecast(city.id).catch { e ->
                if(e is CancellationException) throw e
                if(identity==generation) fail(AppFailure(FailureKind.STORAGE,e.message))
            }.collect { value ->
                currentCoroutineContext().ensureActive()
                if(identity!=generation)return@collect
                snapshot=value;publish()
                if(first){first=false;refresh()}
            }
        }
    }
    private fun publish() {
        val city=state.value.city?:return
        val data=snapshot
        if(data==null) {mutable.update{it.copy(content=ForecastContent.InitialLoading(city))};return}
        val previous=state.value.content as? ForecastContent.Ready
        val window=dateWindow(clock,city.zone)
        if(selection !in window)selection=window.first()
        saved["date"]=selection.toString()
        val days=window.map { date ->
            val weather=data.days[date]
            val old=previous?.days?.firstOrNull{it.date==date}
            if(old!=null && old.weather==weather)old
            else ForecastDayUi(date,weather,weather?.let{ActivityScorer.rank(it)}?:emptyList())
        }
        mutable.update{it.copy(content=ForecastContent.Ready(data.city,days,selection!!,data.lastSuccessfulUpdate))}
    }
    fun select(date:LocalDate) {
        val ready=state.value.content as? ForecastContent.Ready?:return
        if(ready.days.none{it.date==date})return
        selection=date;saved["date"]=date.toString();mutable.update{it.copy(content=ready.copy(selectedDate=date))}
    }
    fun refresh() {
        val city=state.value.city?:return
        if(refreshJob?.isActive==true)return
        val failure=(state.value.refresh as? RefreshStatus.Failed)?.failure
        if(failure?.retryAt?.isAfter(clock.instant())==true)return
        val identity=generation
        mutable.update{it.copy(refresh=RefreshStatus.Refreshing)}
        if(collection?.isActive!=true)observe(city,identity)
        refreshJob=viewModelScope.launch(dispatchers.main) {
            val result=repository.refresh(city)
            currentCoroutineContext().ensureActive()
            if(identity!=generation)return@launch
            when(result) {
                is RefreshOutcome.Success -> mutable.update{it.copy(refresh=RefreshStatus.Idle)}
                is RefreshOutcome.Failed -> fail(result.failure)
            }
        }
    }
    private fun fail(failure:AppFailure) {
        mutable.update { old -> old.copy(refresh=RefreshStatus.Failed(failure),content=
            if(old.content is ForecastContent.Ready)old.content else ForecastContent.InitialError(old.city!!,failure)) }
    }
    fun onResume(){reconcileDate();scheduleMidnight()}
    fun onBackground(){midnight?.cancel()}
    fun reconcileDate() {
        val city=state.value.city?:return
        val today=dateWindow(clock,city.zone).first()
        if(today!=windowStart) {windowStart=today;publish();refresh()}
    }
    private fun scheduleMidnight() {
        midnight?.cancel()
        val city=state.value.city?:return
        midnight=viewModelScope.launch(dispatchers.main) {
            while(isActive) {
                val next=LocalDate.now(clock.withZone(city.zone)).plusDays(1).atStartOfDay(city.zone).toInstant()
                delay(Duration.between(clock.instant(),next).toMillis().coerceAtLeast(1))
                reconcileDate()
            }
        }
    }
    fun leave(){generation++;cancelJobs();snapshot=null;selection=null;saved.remove<ArrayList<String>>("city");saved.remove<String>("date");mutable.value=ForecastState()}
    private fun cancelJobs(){collection?.cancel();refreshJob?.cancel();midnight?.cancel()}
}
