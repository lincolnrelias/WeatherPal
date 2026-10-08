package com.example.weatherpal.domain.scoring

import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.scoring.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ActivityScorerTest {
    private val date = LocalDate.of(2026, 10, 7)

    private fun day(
        t: Double? = 22.0,
        p: Double? = 0.0,
        w: Double? = 10.0,
        d: Double? = 0.0,
        s: Double? = null,
    ) = DailyWeather(date, t, p, w, d, s)

    private fun scores(day: DailyWeather) =
        ActivityScorer.rank(day).associate { it.activity to it.score }

    @Test
    fun workedExamplesAndTieOrder() {
        assertEquals(
            listOf(
                Activity.SURFING,
                Activity.OUTDOOR_SIGHTSEEING,
                Activity.SKIING,
                Activity.INDOOR_SIGHTSEEING,
            ),
            ActivityScorer.rank(day()).map { it.activity },
        )
        assertEquals(listOf(100, 100, 15, 0), ActivityScorer.rank(day()).map { it.score })
        assertEquals(
            listOf(100, 60, 40, 40),
            ActivityScorer.rank(day(-5.0, 2.0, 10.0, 40.0)).map { it.score },
        )
    }

    @Test
    fun everyComponentBoundary() {
        fun check(f: (Double) -> Int, cases: List<Pair<Double, List<Int>>>) {
            cases.forEach { (boundary, points) ->
                listOf(boundary - 0.00001, boundary, boundary + 0.00001).zip(points).forEach {
                    (v, expected) ->
                    assertEquals("at $v", expected, f(v))
                }
            }
        }
        check(
            ActivityScorer::outdoorTemperature,
            listOf(
                0.0 to listOf(0, 10, 10),
                5.0 to listOf(10, 30, 30),
                15.0 to listOf(30, 45, 45),
                25.0 to listOf(45, 45, 30),
                30.0 to listOf(30, 30, 10),
                35.0 to listOf(10, 10, 0),
            ),
        )
        check(
            ActivityScorer::outdoorRain,
            listOf(1.0 to listOf(35, 20, 20), 5.0 to listOf(20, 5, 5), 15.0 to listOf(5, 0, 0)),
        )
        check(
            ActivityScorer::outdoorWind,
            listOf(15.0 to listOf(20, 20, 10), 30.0 to listOf(10, 10, 0)),
        )
        check(
            ActivityScorer::surfTemperature,
            listOf(
                10.0 to listOf(0, 10, 10),
                15.0 to listOf(10, 30, 30),
                20.0 to listOf(30, 45, 45),
                30.0 to listOf(45, 45, 30),
                35.0 to listOf(30, 30, 0),
            ),
        )
        check(ActivityScorer::surfRain, listOf(1.0 to listOf(30, 15, 15), 5.0 to listOf(15, 0, 0)))
        check(
            ActivityScorer::surfWind,
            listOf(15.0 to listOf(25, 25, 10), 25.0 to listOf(10, 10, 0)),
        )
        check(
            ActivityScorer::skiTemperature,
            listOf(
                -20.0 to listOf(0, 15, 15),
                -10.0 to listOf(15, 25, 25),
                0.0 to listOf(25, 25, 15),
                5.0 to listOf(15, 15, 0),
            ),
        )
        check(ActivityScorer::skiWind, listOf(15.0 to listOf(15, 15, 8), 30.0 to listOf(8, 8, 0)))
        check(
            ActivityScorer::depthPoints,
            listOf(0.0 to listOf(0, 0, 15), 10.0 to listOf(15, 40, 40), 30.0 to listOf(40, 60, 60)),
        )
        check(
            ActivityScorer::snowfallPoints,
            listOf(0.0 to listOf(0, 0, 15), 3.0 to listOf(15, 30, 30), 10.0 to listOf(30, 45, 45)),
        )
    }

    @Test
    fun labels() {
        listOf(
                0 to Suitability.UNFAVORABLE,
                39 to Suitability.UNFAVORABLE,
                40 to Suitability.MIXED,
                69 to Suitability.MIXED,
                70 to Suitability.FAVORABLE,
                89 to Suitability.FAVORABLE,
                90 to Suitability.VERY_FAVORABLE,
                100 to Suitability.VERY_FAVORABLE,
            )
            .forEach { (s, l) -> assertEquals(l, ActivityScorer.label(s)) }
        assertEquals(Suitability.INSUFFICIENT_DATA, ActivityScorer.label(null))
    }

    @Test
    fun missingInputsZeroDepthAndFallback() {
        assertEquals(39, scores(day(-5.0, d = 0.0, s = 100.0))[Activity.SKIING])
        assertEquals(85, scores(day(-5.0, d = null, s = 10.0))[Activity.SKIING])
        assertNull(scores(day(d = null, s = null))[Activity.SKIING])
        assertEquals(100, scores(day(d = null, s = null))[Activity.SURFING])
        assertNull(scores(day(p = null))[Activity.INDOOR_SIGHTSEEING])
        assertNull(scores(day(w = -1.0))[Activity.SKIING])
        assertNull(scores(day(t = Double.NaN))[Activity.SURFING])
        assertNull(scores(day(p = Double.POSITIVE_INFINITY))[Activity.OUTDOOR_SIGHTSEEING])
    }

    @Test
    fun scoresBoundedComplementaryAndDeterministic() {
        for (t in -40..45) for (p in listOf(0.0, 1.0, 5.0, 15.0, 50.0)) for (w in
            listOf(0.0, 15.0, 25.0, 30.0, 70.0)) {
            val input = day(t.toDouble(), p, w, 40.0)
            val ranks = ActivityScorer.rank(input)
            assertTrue(ranks.all { it.score!! in 0..100 })
            val scores = scores(input)
            assertEquals(
                100,
                scores[Activity.OUTDOOR_SIGHTSEEING]!! + scores[Activity.INDOOR_SIGHTSEEING]!!,
            )
            assertEquals(ranks, ActivityScorer.rank(input))
        }
    }
}
