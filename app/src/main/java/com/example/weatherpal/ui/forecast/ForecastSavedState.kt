package com.example.weatherpal.ui.forecast

import androidx.lifecycle.SavedStateHandle
import com.example.weatherpal.domain.model.City
import java.time.LocalDate
import java.time.ZoneId

/** Saved state is a serialized copy; ForecastState.Open owns the live city and selection. */
internal class ForecastSavedState(private val saved: SavedStateHandle) {
    data class Restored(val city: City, val selectedDate: LocalDate?)

    fun restore(): Restored? {
        val fields = saved.get<HashMap<String, String>>("forecast.city")
            ?: saved.get<ArrayList<String>>("city")?.takeIf { it.size == 7 }?.let {
                hashMapOf("id" to it[0], "name" to it[1], "region" to it[2],
                    "country" to it[3], "latitude" to it[4], "longitude" to it[5],
                    "timezone" to it[6])
            } ?: return null
        return runCatching {
            val city = City(
                fields.getValue("id").toLong(), fields.getValue("name"),
                fields["region"]?.ifEmpty { null }, fields["country"]?.ifEmpty { null },
                fields.getValue("latitude").toDouble(), fields.getValue("longitude").toDouble(),
                fields.getValue("timezone"),
            )
            require(city.id > 0 && city.name.isNotBlank())
            require(city.latitude.isFinite() && city.latitude in -90.0..90.0)
            require(city.longitude.isFinite() && city.longitude in -180.0..180.0)
            ZoneId.of(city.timezone)
            val date = saved.get<String>("forecast.date") ?: saved.get<String>("date")
            Restored(city, date?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
        }.getOrNull()
    }

    fun save(city: City, date: LocalDate) {
        clear()
        saved["forecast.city"] = hashMapOf(
            "id" to city.id.toString(), "name" to city.name,
            "region" to (city.region ?: ""), "country" to (city.country ?: ""),
            "latitude" to city.latitude.toString(), "longitude" to city.longitude.toString(),
            "timezone" to city.timezone,
        )
        select(date)
    }

    fun select(date: LocalDate) {
        saved["forecast.date"] = date.toString()
    }

    fun clear() {
        saved.remove<HashMap<String, String>>("forecast.city")
        saved.remove<String>("forecast.date")
        saved.remove<ArrayList<String>>("city")
        saved.remove<String>("date")
    }
}
