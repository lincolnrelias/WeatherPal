package com.example.weatherpal.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.example.weatherpal.core.AppDispatchers
import com.example.weatherpal.domain.model.City
import com.example.weatherpal.domain.model.DailyWeather
import com.example.weatherpal.domain.model.ForecastSnapshot
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ViewModelTestFixture {
    protected val dispatcher = StandardTestDispatcher()
    protected val dispatchers = AppDispatchers(dispatcher)
    protected val clock = MutableClock()
    protected val city = City(1, "Berlin", null, "Germany", 52.0, 13.0, "Europe/Berlin")
    protected val date = LocalDate.of(2026, 10, 7)
    protected val weather = FakeWeatherRepository()
    protected val viewModels = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        viewModels.forEach {
            ViewModelStore().apply {
                put("vm", it)
                clear()
            }
        }
        Dispatchers.resetMain()
    }

    protected fun scenario(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                viewModels.forEach {
                    ViewModelStore().apply {
                        put("vm", it)
                        clear()
                    }
                }
            }
        }

    protected fun snapshot(temp: Double = 22.0, time: Instant = clock.now) =
        ForecastSnapshot(
            city,
            (0..6).associate { i ->
                val d = date.plusDays(i.toLong())
                d to DailyWeather(d, temp, 0.0, 10.0, 0.0, 0.0)
            },
            time,
        )
}
