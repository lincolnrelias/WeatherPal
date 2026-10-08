package com.example.weatherpal.data.local

import androidx.room.*
import com.example.weatherpal.domain.model.*
import java.time.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cities")
data class CachedCity(
    @PrimaryKey val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val updated: Long,
) {
    fun city() = City(id, name, region, country, latitude, longitude, timezone)
}

@Entity(
    tableName = "days",
    primaryKeys = ["cityId", "date"],
    foreignKeys =
        [
            ForeignKey(
                entity = CachedCity::class,
                parentColumns = ["id"],
                childColumns = ["cityId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class CachedForecastDay(
    val cityId: Long,
    val date: String,
    val temperature: Double?,
    val precipitation: Double?,
    val wind: Double?,
    val depth: Double?,
    val snowfall: Double?,
    val validSamples: Int,
    val expectedSamples: Int,
) {
    fun weather() =
        DailyWeather(
            LocalDate.parse(date),
            temperature,
            precipitation,
            wind,
            depth,
            snowfall,
            validSamples,
            expectedSamples,
        )
}

data class CityWithDays(
    @Embedded val header: CachedCity,
    @Relation(parentColumn = "id", entityColumn = "cityId") val days: List<CachedForecastDay>,
) {
    fun snapshot() =
        ForecastSnapshot(
            header.city(),
            days.associate { LocalDate.parse(it.date) to it.weather() },
            Instant.ofEpochMilli(header.updated),
        )
}

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

@Database(
    entities = [CachedCity::class, CachedForecastDay::class],
    version = 1,
    exportSchema = true,
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun dao(): WeatherDao
}
