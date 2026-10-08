package com.example.weatherpal.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM cities ORDER BY updated DESC, id ASC")
    fun observeCities(): Flow<List<CachedCity>>

    @Query("SELECT * FROM cities ORDER BY updated ASC, id ASC")
    suspend fun oldestFirst(): List<CachedCity>

    @Transaction
    @Query("SELECT * FROM cities WHERE id = :id")
    fun observeForecast(id: Long): Flow<CityWithDays?>

    @Transaction
    @Query("SELECT * FROM cities WHERE id = :id")
    suspend fun read(id: Long): CityWithDays?

    @Insert suspend fun insertCity(city: CachedCity)

    @Insert suspend fun insertDay(day: CachedForecastDay)

    @Query("DELETE FROM cities WHERE id = :id") suspend fun deleteCity(id: Long)

    @Query("DELETE FROM days WHERE cityId = :id AND date = :date")
    suspend fun deleteDay(id: Long, date: String)

    @Query("UPDATE cities SET updated = :value WHERE id = :id")
    suspend fun timestamp(id: Long, value: Long)
}
