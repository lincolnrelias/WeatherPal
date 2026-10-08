package com.example.weatherpal.domain.weather

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WeatherNormalizationTest {
    private val date = LocalDate.of(2026, 10, 7)

    @Test
    fun snowCoverageMedianAndDaylightSaving() {
        val utc = ZoneId.of("UTC")
        assertNull(aggregateSnow(date, utc, List(11) { 0.3 }).centimeters)
        assertEquals(30.0, aggregateSnow(date, utc, List(12) { 0.3 }).centimeters!!, 0.0)
        assertEquals(
            6.5,
            aggregateSnow(date, utc, (1..12).map { it / 100.0 }).centimeters!!,
            0.00001,
        )
        assertEquals(
            7.0,
            aggregateSnow(date, utc, (1..13).map { it / 100.0 }).centimeters!!,
            0.00001,
        )
        val ny = ZoneId.of("America/New_York")
        val spring = LocalDate.of(2026, 3, 8)
        val fall = LocalDate.of(2026, 11, 1)
        assertEquals(23, expectedHours(spring, ny))
        assertEquals(25, expectedHours(fall, ny))
        assertNotNull(aggregateSnow(spring, ny, List(12) { 0.0 }).centimeters)
        assertNull(aggregateSnow(fall, ny, List(12) { 0.0 }).centimeters)
        assertNotNull(aggregateSnow(fall, ny, List(13) { 0.0 }).centimeters)
        assertEquals(
            0,
            aggregateSnow(date, utc, listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
                .validSamples,
        )
    }

    @Test
    fun cityLocalWindow() {
        val clock = Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"), ZoneOffset.UTC)
        assertEquals(date, dateWindow(clock, ZoneId.of("America/Sao_Paulo")).first())
        assertEquals(date.plusDays(1), dateWindow(clock, ZoneId.of("Asia/Tokyo")).first())
    }
}
