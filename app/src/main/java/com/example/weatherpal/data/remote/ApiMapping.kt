package com.example.weatherpal.data.remote

import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.scoring.*
import java.time.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

fun mapCities(dto: GeocodingDto): List<City> {
    val entries = dto.results.orEmpty()
    val cities =
        entries
            .mapNotNull { entry ->
                val item =
                    runCatching { ApiFactory.json.decodeFromJsonElement<CityDto>(entry) }
                        .getOrNull() ?: return@mapNotNull null
                val id = item.id?.takeIf { it > 0 } ?: return@mapNotNull null
                val name = item.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val lat =
                    item.latitude.valid()?.takeIf { it in -90.0..90.0 } ?: return@mapNotNull null
                val lon =
                    item.longitude.valid()?.takeIf { it in -180.0..180.0 } ?: return@mapNotNull null
                val zone =
                    item.timezone?.takeIf { it in ZoneId.getAvailableZoneIds() }
                        ?: return@mapNotNull null
                City(
                    id,
                    name,
                    item.admin1?.takeIf { it.isNotBlank() },
                    item.country?.takeIf { it.isNotBlank() },
                    lat,
                    lon,
                    zone,
                )
            }
            .distinctBy { it.id }
    if (entries.isNotEmpty() && cities.isEmpty()) malformed("No valid geocoding entries")
    return cities
}

private fun JsonElement?.number(nonnegative: Boolean): Double? =
    (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull().valid(nonnegative)

fun mapForecast(dto: ForecastDto, city: City, clock: Clock): Map<LocalDate, DailyWeather> {
    if (dto.timezone != city.timezone || runCatching { ZoneId.of(dto.timezone) }.isFailure)
        malformed("Timezone mismatch")
    val daily = dto.daily ?: malformed("Missing daily block")
    val axis = daily.time?.takeIf { it.isNotEmpty() } ?: malformed("Missing daily dates")
    val dates =
        try {
            axis.map { LocalDate.parse(it) }
        } catch (e: DateTimeException) {
            malformed("Invalid date")
        }
    if (dates.zipWithNext().any { (a, b) -> a >= b })
        malformed("Dates must be unique and increasing")
    val fields =
        listOf(
            "temperature_2m_mean" to daily.temperature_2m_mean,
            "precipitation_sum" to daily.precipitation_sum,
            "wind_speed_10m_max" to daily.wind_speed_10m_max,
            "snowfall_sum" to daily.snowfall_sum,
        )
    val units =
        mapOf(
            "temperature_2m_mean" to "°C",
            "precipitation_sum" to "mm",
            "wind_speed_10m_max" to "km/h",
            "snowfall_sum" to "cm",
        )
    fields.forEach { (field, values) ->
        if (values != null) {
            if (values.size != dates.size) malformed("Length mismatch: $field")
            if (dto.daily_units?.get(field) != units[field]) malformed("Unsupported unit: $field")
        }
    }
    val hourly =
        runCatching { dto.hourly?.let { ApiFactory.json.decodeFromJsonElement<HourlyDto>(it) } }
            .getOrNull()
    val snowSamples =
        if (
            ((dto.hourly_units as? JsonObject)?.get("snow_depth") as? JsonPrimitive)
                ?.contentOrNull != "m" ||
                hourly?.time == null ||
                hourly.snow_depth == null ||
                hourly.time.size != hourly.snow_depth.size
        )
            emptyMap()
        else
            runCatching {
                    hourly.time
                        .mapIndexed { i, time ->
                            LocalDateTime.parse(time).toLocalDate() to
                                hourly.snow_depth[i].number(true)
                        }
                        .groupBy({ it.first }, { it.second })
                }
                .getOrDefault(emptyMap())
    val window = dateWindow(clock, city.zone).toSet()
    val result =
        dates
            .mapIndexed { i, date ->
                val depth = aggregateSnow(date, city.zone, snowSamples[date].orEmpty())
                date to
                    DailyWeather(
                        date,
                        daily.temperature_2m_mean?.get(i).number(false),
                        daily.precipitation_sum?.get(i).number(true),
                        daily.wind_speed_10m_max?.get(i).number(true),
                        depth.centimeters,
                        daily.snowfall_sum?.get(i).number(true),
                        depth.validSamples,
                        depth.expectedSamples,
                    )
            }
            .filter { it.first in window }
            .toMap()
    if (result.values.none { ActivityScorer.rank(it).any { rank -> rank.score != null } })
        throw DataFailure(
            AppFailure(FailureKind.UNUSABLE_FORECAST, "No computable activities in current window")
        )
    return result
}
