package com.example.weatherpal.data.local

import androidx.room.*
import com.example.weatherpal.domain.model.*
import java.time.Instant
import java.time.LocalDate

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
