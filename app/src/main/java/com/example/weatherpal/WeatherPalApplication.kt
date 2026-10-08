package com.example.weatherpal

import android.app.Application
import com.example.weatherpal.di.AppContainer

class WeatherPalApplication : Application() {
    val container by lazy { AppContainer(this) }
}
