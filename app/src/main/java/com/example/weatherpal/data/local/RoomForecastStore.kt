package com.example.weatherpal.data.local

import androidx.room.withTransaction
import com.example.weatherpal.domain.model.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomForecastStore(private val db: WeatherDatabase) : ForecastStore {
    private val writes = Mutex()
    private val dao = db.dao()

    override fun cities() =
        dao.observeCities().map { rows ->
            rows.map { CachedCitySummary(it.city(), Instant.ofEpochMilli(it.updated)) }
        }

    override fun forecast(id: Long) =
        dao.observeForecast(id).map { it?.snapshot() }.distinctUntilChanged()

    override suspend fun prune(policy: CachePolicy) =
        writes.withLock {
            db.withTransaction {
                val rows = dao.oldestFirst()
                rows.take((rows.size - policy.maxCities).coerceAtLeast(0)).forEach {
                    dao.deleteCity(it.id)
                }
            }
        }

    // Identifiers come exclusively from our schema; values are always bound parameters.
    private fun updateColumns(table: String, changes: Map<String, Any?>, keys: Map<String, Any?>) {
        if (changes.isEmpty()) return
        val sql =
            "UPDATE $table SET ${changes.keys.joinToString { "$it = ?" }} WHERE ${keys.keys.joinToString(" AND ") { "$it = ?" }}"
        db.openHelper.writableDatabase.execSQL(sql, (changes.values + keys.values).toTypedArray())
    }

    override suspend fun commit(
        city: City,
        days: Map<LocalDate, DailyWeather>,
        updated: Instant,
        policy: CachePolicy,
    ): Boolean =
        writes.withLock {
            currentCoroutineContext().ensureActive()
            db.withTransaction {
                currentCoroutineContext().ensureActive()
                val old = dao.read(city.id)
                var changed = old == null
                if (old == null) {
                    val entries = dao.oldestFirst()
                    entries.take((entries.size - policy.maxCities + 1).coerceAtLeast(0)).forEach {
                        dao.deleteCity(it.id)
                    }
                    dao.insertCity(
                        CachedCity(
                            city.id,
                            city.name,
                            city.region,
                            city.country,
                            city.latitude,
                            city.longitude,
                            city.timezone,
                            updated.toEpochMilli(),
                        )
                    )
                } else {
                    val before = old.header.city()
                    val metadata = linkedMapOf<String, Any?>()
                    if (before.name != city.name) metadata["name"] = city.name
                    if (before.region != city.region) metadata["region"] = city.region
                    if (before.country != city.country) metadata["country"] = city.country
                    if (before.latitude != city.latitude) metadata["latitude"] = city.latitude
                    if (before.longitude != city.longitude) metadata["longitude"] = city.longitude
                    if (before.timezone != city.timezone) metadata["timezone"] = city.timezone
                    changed = metadata.isNotEmpty()
                    updateColumns("cities", metadata, mapOf("id" to city.id))
                    dao.timestamp(city.id, updated.toEpochMilli())
                }
                val existing = old?.days.orEmpty().associateBy { it.date }
                val replacement =
                    days.values.associate { w ->
                        w.date.toString() to
                            CachedForecastDay(
                                city.id,
                                w.date.toString(),
                                w.temperatureC,
                                w.precipitationMm,
                                w.windKmh,
                                w.snowDepthCm,
                                w.snowfallCm,
                                w.depthValidSamples,
                                w.depthExpectedSamples,
                            )
                    }
                existing.keys
                    .filter { it !in replacement }
                    .forEach {
                        dao.deleteDay(city.id, it)
                        changed = true
                    }
                replacement.forEach { (date, row) ->
                    val previous = existing[date]
                    if (previous == null) {
                        dao.insertDay(row)
                        changed = true
                    } else if (previous != row) {
                        val fields = linkedMapOf<String, Any?>()
                        if (previous.temperature != row.temperature)
                            fields["temperature"] = row.temperature
                        if (previous.precipitation != row.precipitation)
                            fields["precipitation"] = row.precipitation
                        if (previous.wind != row.wind) fields["wind"] = row.wind
                        if (previous.depth != row.depth) fields["depth"] = row.depth
                        if (previous.snowfall != row.snowfall) fields["snowfall"] = row.snowfall
                        if (previous.validSamples != row.validSamples)
                            fields["validSamples"] = row.validSamples
                        if (previous.expectedSamples != row.expectedSamples)
                            fields["expectedSamples"] = row.expectedSamples
                        updateColumns("days", fields, mapOf("cityId" to city.id, "date" to date))
                        changed = true
                    }
                }
                currentCoroutineContext().ensureActive()
                changed
            }
        }
}
