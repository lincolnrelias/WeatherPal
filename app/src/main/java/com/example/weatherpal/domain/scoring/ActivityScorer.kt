package com.example.weatherpal.domain.scoring

import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.weather.*

object ActivityScorer {
    internal fun outdoorTemperature(t: Double) =
        when {
            t in 15.0..25.0 -> 45
            t >= 5 && t < 15 || t > 25 && t <= 30 -> 30
            t >= 0 && t < 5 || t > 30 && t <= 35 -> 10
            else -> 0
        }

    internal fun outdoorRain(p: Double) =
        when {
            p < 1 -> 35
            p < 5 -> 20
            p < 15 -> 5
            else -> 0
        }

    internal fun outdoorWind(w: Double) =
        when {
            w <= 15 -> 20
            w <= 30 -> 10
            else -> 0
        }

    internal fun surfTemperature(t: Double) =
        when {
            t in 20.0..30.0 -> 45
            t >= 15 && t < 20 || t > 30 && t <= 35 -> 30
            t >= 10 && t < 15 -> 10
            else -> 0
        }

    internal fun surfRain(p: Double) =
        when {
            p < 1 -> 30
            p < 5 -> 15
            else -> 0
        }

    internal fun surfWind(w: Double) =
        when {
            w <= 15 -> 25
            w <= 25 -> 10
            else -> 0
        }

    internal fun skiTemperature(t: Double) =
        when {
            t in -10.0..0.0 -> 25
            t >= -20 && t < -10 || t > 0 && t <= 5 -> 15
            else -> 0
        }

    internal fun skiWind(w: Double) =
        when {
            w <= 15 -> 15
            w <= 30 -> 8
            else -> 0
        }

    internal fun depthPoints(d: Double) =
        when {
            d >= 30 -> 60
            d >= 10 -> 40
            d > 0 -> 15
            else -> 0
        }

    internal fun snowfallPoints(s: Double) =
        when {
            s >= 10 -> 45
            s >= 3 -> 30
            s > 0 -> 15
            else -> 0
        }

    fun label(score: Int?) =
        when {
            score == null -> Suitability.INSUFFICIENT_DATA
            score >= 90 -> Suitability.VERY_FAVORABLE
            score >= 70 -> Suitability.FAVORABLE
            score >= 40 -> Suitability.MIXED
            else -> Suitability.UNFAVORABLE
        }

    private fun missing(activity: Activity) =
        Recommendation(activity, null, label(null), listOf(Reason(ReasonCode.MISSING_INPUTS)))

    private fun recommendation(
        activity: Activity,
        reasons: List<Reason>,
        source: SnowSource? = null,
    ): Recommendation {
        var score = reasons.sumOf { it.points ?: 0 }.coerceIn(0, 100)
        if (reasons.any { it.code == ReasonCode.ZERO_SNOW_CAP }) score = score.coerceAtMost(39)
        return Recommendation(activity, score, label(score), reasons, source)
    }

    fun rank(day: DailyWeather): List<Recommendation> {
        val t = day.temperatureC.valid()
        val p = day.precipitationMm.valid(true)
        val w = day.windKmh.valid(true)
        val d = day.snowDepthCm.valid(true)
        val s = day.snowfallCm.valid(true)
        val outdoor =
            if (t == null || p == null || w == null) missing(Activity.OUTDOOR_SIGHTSEEING)
            else
                recommendation(
                    Activity.OUTDOOR_SIGHTSEEING,
                    listOf(
                        Reason(ReasonCode.TEMPERATURE, t, outdoorTemperature(t)),
                        Reason(ReasonCode.PRECIPITATION, p, outdoorRain(p)),
                        Reason(ReasonCode.WIND, w, outdoorWind(w)),
                    ),
                )
        val surf =
            if (t == null || p == null || w == null) missing(Activity.SURFING)
            else
                recommendation(
                    Activity.SURFING,
                    listOf(
                        Reason(ReasonCode.TEMPERATURE, t, surfTemperature(t)),
                        Reason(ReasonCode.PRECIPITATION, p, surfRain(p)),
                        Reason(ReasonCode.WIND, w, surfWind(w)),
                    ),
                )
        val snow = d ?: s
        val ski =
            if (t == null || w == null || snow == null) missing(Activity.SKIING)
            else
                recommendation(
                    Activity.SKIING,
                    buildList {
                        add(
                            if (d != null) Reason(ReasonCode.SNOW_DEPTH, d, depthPoints(d))
                            else Reason(ReasonCode.SNOWFALL, s, snowfallPoints(s!!))
                        )
                        add(Reason(ReasonCode.TEMPERATURE, t, skiTemperature(t)))
                        add(Reason(ReasonCode.WIND, w, skiWind(w)))
                        if (snow == 0.0) add(Reason(ReasonCode.ZERO_SNOW_CAP))
                    },
                    if (d != null) SnowSource.DEPTH else SnowSource.SNOWFALL,
                )
        val indoor =
            outdoor.score?.let { score ->
                Recommendation(
                    Activity.INDOOR_SIGHTSEEING,
                    100 - score,
                    label(100 - score),
                    listOf(Reason(ReasonCode.INDOOR_TRADEOFF, score.toDouble())) + outdoor.reasons,
                )
            } ?: missing(Activity.INDOOR_SIGHTSEEING)
        return listOf(ski, surf, outdoor, indoor)
            .sortedWith(
                compareByDescending<Recommendation> { it.score ?: -1 }
                    .thenBy { it.activity.ordinal }
            )
    }
}
