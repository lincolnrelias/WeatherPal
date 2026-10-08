package com.example.weatherpal.di

import android.content.Context
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import com.example.weatherpal.data.local.*
import com.example.weatherpal.data.remote.*
import com.example.weatherpal.data.repository.CachedWeatherRepository
import com.example.weatherpal.domain.model.CachePolicy
import com.example.weatherpal.domain.repository.*
import com.example.weatherpal.ui.forecast.ForecastViewModel
import com.example.weatherpal.ui.search.SearchViewModel
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val computation: CoroutineDispatcher = Dispatchers.Default,
)

class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()
    val dispatchers = AppDispatchers()
    private val policy = CachePolicy(maxCities = 3)
    private val db =
        Room.databaseBuilder(context, WeatherDatabase::class.java, "weatherpal.db").build()
    private val client = ApiFactory.client()
    private val geo =
        ApiFactory.retrofit("https://geocoding-api.open-meteo.com/", client)
            .create(GeocodingService::class.java)
    private val forecast =
        ApiFactory.retrofit("https://api.open-meteo.com/", client)
            .create(ForecastService::class.java)
    val cities: CityRepository = RemoteCityRepository(geo, clock)
    val weather: WeatherRepository =
        CachedWeatherRepository(
            OpenMeteoRemote(forecast, clock),
            RoomForecastStore(db),
            clock,
            policy,
        )
    val factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                when (modelClass) {
                    SearchViewModel::class.java ->
                        SearchViewModel(
                            cities,
                            weather,
                            clock,
                            dispatchers,
                            extras.createSavedStateHandle(),
                        )
                    ForecastViewModel::class.java ->
                        ForecastViewModel(
                            weather,
                            clock,
                            dispatchers,
                            extras.createSavedStateHandle(),
                        )
                    else -> error("Unknown ViewModel: $modelClass")
                }
                    as T
        }
}
