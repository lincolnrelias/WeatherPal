package com.example.weatherpal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedCity::class, CachedForecastDay::class],
    version = 1,
    exportSchema = true,
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun dao(): WeatherDao
}
