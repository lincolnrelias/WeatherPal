package com.example.weatherpal.domain.scoring

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

fun dateWindow(clock: Clock, zone: ZoneId): List<LocalDate> {
    val today = LocalDate.now(clock.withZone(zone))
    return List(7) { today.plusDays(it.toLong()) }
}

fun expectedHours(date: LocalDate, zone: ZoneId): Int =
    Duration.between(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone)).toHours().toInt()

fun Double?.valid(nonnegative: Boolean = false): Double? =
    this?.takeIf { it.isFinite() && (!nonnegative || it >= 0) }?.let { if (it == 0.0) 0.0 else it }

data class SnowDepth(val centimeters: Double?, val validSamples: Int, val expectedSamples: Int)

fun aggregateSnow(date: LocalDate, zone: ZoneId, samplesMeters: List<Double?>): SnowDepth {
    val samples = samplesMeters.mapNotNull { it.valid(true)?.times(100)?.valid(true) }.sorted()
    val expected = expectedHours(date, zone)
    val median =
        if (samples.isEmpty() || samples.size < (expected + 1) / 2) null
        else if (samples.size % 2 == 1) samples[samples.size / 2]
        else samples[samples.size / 2 - 1] / 2 + samples[samples.size / 2] / 2
    return SnowDepth(median, samples.size, expected)
}
