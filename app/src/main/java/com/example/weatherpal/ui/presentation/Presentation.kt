package com.example.weatherpal.ui.presentation

import com.example.weatherpal.domain.model.*
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

fun City.description() = listOfNotNull(region, country).distinct().joinToString(", ")

fun number(value: Double?, unit: String) =
    value?.let { String.format(Locale.getDefault(), "%.1f %s", it, unit) } ?: "Unavailable"

fun Activity.title() =
    when (this) {
        Activity.SKIING -> "Skiing"
        Activity.SURFING -> "Surfing"
        Activity.OUTDOOR_SIGHTSEEING -> "Outdoor sightseeing"
        Activity.INDOOR_SIGHTSEEING -> "Indoor sightseeing"
    }

fun Suitability.title() =
    when (this) {
        Suitability.VERY_FAVORABLE -> "Very favorable"
        Suitability.FAVORABLE -> "Favorable"
        Suitability.MIXED -> "Mixed"
        Suitability.UNFAVORABLE -> "Unfavorable"
        Suitability.INSUFFICIENT_DATA -> "Insufficient data"
    }

fun AppFailure.message() =
    when (kind) {
        FailureKind.VALIDATION -> "Enter at least two characters to search."
        FailureKind.NETWORK -> "Couldn’t connect. Check your connection and retry."
        FailureKind.TIMEOUT -> "The request took too long. Please retry."
        FailureKind.RATE_LIMITED -> "The weather service is busy. Please wait before retrying."
        FailureKind.HTTP -> "The weather service couldn’t complete this request. Please retry."
        FailureKind.MALFORMED_RESPONSE -> "The service returned unexpected data. Please retry."
        FailureKind.UNUSABLE_FORECAST ->
            "No usable forecast is available for these dates. Please retry."
        FailureKind.STORAGE -> "Couldn’t read or save the forecast on this device. Please retry."
        FailureKind.CANCELLED -> "The refresh was interrupted. Please retry."
    }

fun reasonText(reason: Reason, activity: Activity): String =
    when (reason.code) {
        ReasonCode.TEMPERATURE -> {
            val band =
                when (activity) {
                    Activity.SKIING ->
                        when (reason.points) {
                            25 -> "in the preferred cold band (−10 to 0°C)"
                            15 -> "in the secondary cold/cool band"
                            else -> "outside the preferred cold bands"
                        }
                    Activity.SURFING ->
                        when (reason.points) {
                            45 -> "in the preferred warm band (20 to 30°C)"
                            30 -> "in a secondary comfort band"
                            10 -> "cool for land-weather comfort"
                            else -> "outside the comfort bands"
                        }
                    else ->
                        when (reason.points) {
                            45 -> "in the preferred mild band (15 to 25°C)"
                            30 -> "in a secondary comfort band"
                            10 -> "cold or hot for outdoor comfort"
                            else -> "outside the comfort bands"
                        }
                }
            "Mean ${number(reason.value,"°C")} — $band."
        }
        ReasonCode.PRECIPITATION ->
            "${number(reason.value,"mm")} precipitation — " +
                when (reason.points) {
                    35,
                    30 -> "mostly dry (under 1 mm)."
                    20,
                    15 -> "some precipitation (1 to under 5 mm)."
                    5 -> "wet conditions (5 to under 15 mm)."
                    else -> "wet conditions reduce outdoor comfort."
                }
        ReasonCode.WIND ->
            "Maximum wind ${number(reason.value,"km/h")} — " +
                when (reason.points) {
                    20,
                    25,
                    15 -> "calmer conditions (up to 15 km/h)."
                    10,
                    8 -> "moderate wind in a secondary comfort band."
                    else -> "stronger wind reduces comfort."
                }
        ReasonCode.SNOW_DEPTH ->
            "Modeled median snow depth ${number(reason.value,"cm")} — " +
                when (reason.points) {
                    60 -> "highest depth band (30 cm or more)."
                    40 -> "middle depth band (10 to under 30 cm)."
                    15 -> "shallow depth band (under 10 cm)."
                    else -> "no modeled snow cover."
                }
        ReasonCode.SNOWFALL ->
            "Snowfall ${number(reason.value,"cm")} used because depth is unavailable; " +
                when (reason.points) {
                    45 -> "10 cm or more."
                    30 -> "3 to under 10 cm."
                    15 -> "under 3 cm."
                    else -> "no fresh snowfall."
                } +
                " Snowfall alone cannot establish a snow base."
        ReasonCode.ZERO_SNOW_CAP -> "The chosen snow signal is zero, so skiing remains unfavorable."
        ReasonCode.INDOOR_TRADEOFF ->
            if ((reason.value ?: 0.0) >= 70)
                "Pleasant outdoor weather lowers the relative appeal of indoor plans."
            else "Indoor plans gain relative appeal because outdoor weather is less comfortable."
        ReasonCode.MISSING_INPUTS ->
            "Required weather inputs are unavailable; no recommendation score was inferred."
    }

fun remainingRetrySeconds(failure: AppFailure?, clock: Clock): Long {
    val deadline = failure?.retryAt ?: return 0
    val remaining = Duration.between(clock.instant(), deadline)
    return if (remaining.isNegative || remaining.isZero) 0
    else remaining.seconds + if (remaining.nano > 0) 1 else 0
}

val updateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm z")
