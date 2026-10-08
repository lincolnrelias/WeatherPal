package com.example.weatherpal.data.remote

import com.example.weatherpal.domain.model.*
import com.example.weatherpal.domain.repository.CityRepository
import com.example.weatherpal.domain.scoring.*
import java.io.IOException
import java.io.InterruptedIOException
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable data class GeocodingDto(val results: List<JsonElement>? = null)

@Serializable
data class CityDto(
    val id: Long? = null,
    val name: String? = null,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
)

@Serializable
data class DailyDto(
    val time: List<String>? = null,
    val temperature_2m_mean: List<JsonElement?>? = null,
    val precipitation_sum: List<JsonElement?>? = null,
    val wind_speed_10m_max: List<JsonElement?>? = null,
    val snowfall_sum: List<JsonElement?>? = null,
)

@Serializable
data class HourlyDto(val time: List<String>? = null, val snow_depth: List<JsonElement?>? = null)

@Serializable
data class ForecastDto(
    val timezone: String? = null,
    val daily: DailyDto? = null,
    val daily_units: Map<String, String>? = null,
    val hourly: JsonElement? = null,
    val hourly_units: JsonElement? = null,
)

interface GeocodingService {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingDto
}

interface ForecastService {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("timezone") timezone: String,
        @Query("forecast_days") days: Int = 7,
        @Query("timeformat") timeformat: String = "iso8601",
        @Query("temperature_unit") temperature: String = "celsius",
        @Query("wind_speed_unit") wind: String = "kmh",
        @Query("precipitation_unit") precipitation: String = "mm",
        @Query("daily")
        daily: String = "temperature_2m_mean,precipitation_sum,wind_speed_10m_max,snowfall_sum",
        @Query("hourly") hourly: String = "snow_depth",
    ): ForecastDto
}

object ApiFactory {
    val json = Json {
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
    }

    fun client() =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            // Permit transport recovery (alternate IPs/stale pooled sockets) within the call
            // timeout.
            // The application performs no automatic request retry loops.
            .retryOnConnectionFailure(true)
            .build()

    fun retrofit(baseUrl: String, client: OkHttpClient) =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}

class DataFailure(val failure: AppFailure) : Exception(failure.diagnostic)

private fun malformed(detail: String): Nothing =
    throw DataFailure(AppFailure(FailureKind.MALFORMED_RESPONSE, detail))

fun failureOf(error: Exception, clock: Clock): AppFailure {
    if (error is CancellationException) throw error
    return when (error) {
        is DataFailure -> error.failure
        is HttpException -> {
            val retryAfter = error.response()?.headers()?.get("Retry-After")
            val retryAt =
                retryAfter
                    ?.toLongOrNull()
                    ?.takeIf { it >= 0 }
                    ?.let { runCatching { clock.instant().plusSeconds(it) }.getOrNull() }
                    ?: retryAfter?.let {
                        runCatching {
                                ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .toInstant()
                            }
                            .getOrNull()
                    }
            AppFailure(
                if (error.code() == 429) FailureKind.RATE_LIMITED else FailureKind.HTTP,
                "HTTP ${error.code()}",
                retryAt,
            )
        }
        is InterruptedIOException -> AppFailure(FailureKind.TIMEOUT, error.message)
        is IOException -> AppFailure(FailureKind.NETWORK, error.message)
        is SerializationException,
        is IllegalArgumentException,
        is DateTimeException -> AppFailure(FailureKind.MALFORMED_RESPONSE, error.message)
        else -> AppFailure(FailureKind.MALFORMED_RESPONSE, error.message)
    }
}

fun mapCities(dto: GeocodingDto): List<City> {
    val entries = dto.results.orEmpty()
    val cities =
        entries
            .mapNotNull { entry ->
                val item =
                    runCatching { ApiFactory.json.decodeFromJsonElement<CityDto>(entry) }
                        .getOrNull() ?: return@mapNotNull null
                val id = item.id?.takeIf { it > 0 } ?: return@mapNotNull null
                val name = item.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val lat =
                    item.latitude.valid()?.takeIf { it in -90.0..90.0 } ?: return@mapNotNull null
                val lon =
                    item.longitude.valid()?.takeIf { it in -180.0..180.0 } ?: return@mapNotNull null
                val zone =
                    item.timezone?.takeIf { it in ZoneId.getAvailableZoneIds() }
                        ?: return@mapNotNull null
                City(
                    id,
                    name,
                    item.admin1?.takeIf { it.isNotBlank() },
                    item.country?.takeIf { it.isNotBlank() },
                    lat,
                    lon,
                    zone,
                )
            }
            .distinctBy { it.id }
    if (entries.isNotEmpty() && cities.isEmpty()) malformed("No valid geocoding entries")
    return cities
}

private fun JsonElement?.number(nonnegative: Boolean): Double? =
    (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull().valid(nonnegative)

fun mapForecast(dto: ForecastDto, city: City, clock: Clock): Map<LocalDate, DailyWeather> {
    if (dto.timezone != city.timezone || runCatching { ZoneId.of(dto.timezone) }.isFailure)
        malformed("Timezone mismatch")
    val daily = dto.daily ?: malformed("Missing daily block")
    val axis = daily.time?.takeIf { it.isNotEmpty() } ?: malformed("Missing daily dates")
    val dates =
        try {
            axis.map { LocalDate.parse(it) }
        } catch (e: DateTimeException) {
            malformed("Invalid date")
        }
    if (dates.zipWithNext().any { (a, b) -> a >= b })
        malformed("Dates must be unique and increasing")
    val fields =
        listOf(
            "temperature_2m_mean" to daily.temperature_2m_mean,
            "precipitation_sum" to daily.precipitation_sum,
            "wind_speed_10m_max" to daily.wind_speed_10m_max,
            "snowfall_sum" to daily.snowfall_sum,
        )
    val units =
        mapOf(
            "temperature_2m_mean" to "°C",
            "precipitation_sum" to "mm",
            "wind_speed_10m_max" to "km/h",
            "snowfall_sum" to "cm",
        )
    fields.forEach { (field, values) ->
        if (values != null) {
            if (values.size != dates.size) malformed("Length mismatch: $field")
            if (dto.daily_units?.get(field) != units[field]) malformed("Unsupported unit: $field")
        }
    }
    val hourly =
        runCatching { dto.hourly?.let { ApiFactory.json.decodeFromJsonElement<HourlyDto>(it) } }
            .getOrNull()
    val snowSamples =
        if (
            ((dto.hourly_units as? JsonObject)?.get("snow_depth") as? JsonPrimitive)
                ?.contentOrNull != "m" ||
                hourly?.time == null ||
                hourly.snow_depth == null ||
                hourly.time.size != hourly.snow_depth.size
        )
            emptyMap()
        else
            runCatching {
                    hourly.time
                        .mapIndexed { i, time ->
                            LocalDateTime.parse(time).toLocalDate() to
                                hourly.snow_depth[i].number(true)
                        }
                        .groupBy({ it.first }, { it.second })
                }
                .getOrDefault(emptyMap())
    val window = dateWindow(clock, city.zone).toSet()
    val result =
        dates
            .mapIndexed { i, date ->
                val depth = aggregateSnow(date, city.zone, snowSamples[date].orEmpty())
                date to
                    DailyWeather(
                        date,
                        daily.temperature_2m_mean?.get(i).number(false),
                        daily.precipitation_sum?.get(i).number(true),
                        daily.wind_speed_10m_max?.get(i).number(true),
                        depth.centimeters,
                        daily.snowfall_sum?.get(i).number(true),
                        depth.validSamples,
                        depth.expectedSamples,
                    )
            }
            .filter { it.first in window }
            .toMap()
    if (result.values.none { ActivityScorer.rank(it).any { rank -> rank.score != null } })
        throw DataFailure(
            AppFailure(FailureKind.UNUSABLE_FORECAST, "No computable activities in current window")
        )
    return result
}

class RemoteCityRepository(private val service: GeocodingService, private val clock: Clock) :
    CityRepository {
    override suspend fun search(query: String): SearchOutcome =
        try {
            SearchOutcome.Success(mapCities(service.search(query.trim())))
        } catch (e: Exception) {
            SearchOutcome.Failed(failureOf(e, clock))
        }
}

interface WeatherRemote {
    suspend fun fetch(city: City): Map<LocalDate, DailyWeather>
}

class OpenMeteoRemote(private val service: ForecastService, private val clock: Clock) :
    WeatherRemote {
    override suspend fun fetch(city: City) =
        mapForecast(service.forecast(city.latitude, city.longitude, city.timezone), city, clock)
}
