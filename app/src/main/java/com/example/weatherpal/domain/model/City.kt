package com.example.weatherpal.domain.model

import java.time.ZoneId

data class City(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    val zone: ZoneId
        get() = ZoneId.of(timezone)
}
