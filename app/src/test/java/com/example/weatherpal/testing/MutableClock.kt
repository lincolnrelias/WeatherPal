package com.example.weatherpal.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(
    var now: Instant = Instant.parse("2026-10-07T12:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone() = zone

    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)

    override fun instant() = now
}
