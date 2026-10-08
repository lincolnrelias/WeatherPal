package com.example.weatherpal.testing

import com.example.weatherpal.data.local.ForecastStore
import com.example.weatherpal.domain.model.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.*

class InMemoryForecastStore : ForecastStore {
    val values = MutableStateFlow<Map<Long, ForecastSnapshot>>(emptyMap())
    var commits = 0
    var fail = false

    override suspend fun prune(policy: CachePolicy) {
        values.value =
            values.value.entries
                .sortedWith(
                    compareByDescending<Map.Entry<Long, ForecastSnapshot>> {
                            it.value.lastSuccessfulUpdate
                        }
                        .thenBy { it.key }
                )
                .take(policy.maxCities)
                .associate { it.toPair() }
    }

    override fun cities() =
        values.map { rows ->
            rows.values
                .sortedWith(
                    compareByDescending<ForecastSnapshot> { it.lastSuccessfulUpdate }
                        .thenBy { it.city.id }
                )
                .map { CachedCitySummary(it.city, it.lastSuccessfulUpdate) }
        }

    override fun forecast(id: Long) = values.map { it[id] }

    override suspend fun commit(
        city: City,
        days: Map<LocalDate, DailyWeather>,
        updated: Instant,
        policy: CachePolicy,
    ): Boolean {
        if (fail) error("disk full")
        val old = values.value[city.id]
        val next = values.value.toMutableMap()
        if (old == null && next.size >= policy.maxCities)
            next.remove(
                next.values
                    .sortedWith(
                        compareBy<ForecastSnapshot> { it.lastSuccessfulUpdate }
                            .thenBy { it.city.id }
                    )
                    .first()
                    .city
                    .id
            )
        next[city.id] = ForecastSnapshot(city, days, updated)
        values.value = next
        commits++
        return old?.days != days || old.city != city
    }
}
