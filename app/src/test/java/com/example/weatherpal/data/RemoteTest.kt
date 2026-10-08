package com.example.weatherpal.data

import com.example.weatherpal.data.remote.*
import com.example.weatherpal.domain.model.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class RemoteTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC)
    private val city = City(1, "Berlin", null, "Germany", 52.52, 13.41, "Europe/Berlin")

    private fun fixture() = javaClass.getResource("/forecast.json")!!.readText()

    private fun map(json: String) =
        mapForecast(ApiFactory.json.decodeFromString<ForecastDto>(json), city, clock)

    @Test
    fun geocodingQueryEncodingAndFiltering() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"results":[{"id":1,"name":"São Paulo","latitude":-23.5,"longitude":-46.6,"timezone":"America/Sao_Paulo"},{"id":1,"name":"Duplicate","latitude":1,"longitude":2,"timezone":"UTC"},{"id":"bad"}]}"""
                    )
            )
            val repo =
                RemoteCityRepository(
                    ApiFactory.retrofit(server.url("/").toString(), ApiFactory.client())
                        .create(GeocodingService::class.java),
                    clock,
                )
            assertEquals(1, (repo.search(" São Paulo & x ") as SearchOutcome.Success).cities.size)
            val request = server.takeRequest().requestUrl!!
            assertEquals("São Paulo & x", request.queryParameter("name"))
            assertEquals("10", request.queryParameter("count"))
            assertEquals("en", request.queryParameter("language"))
            assertEquals("json", request.queryParameter("format"))
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
            val failure = (repo.search("Berlin") as SearchOutcome.Failed).failure
            assertEquals(FailureKind.RATE_LIMITED, failure.kind)
            assertEquals(clock.instant().plusSeconds(60), failure.retryAt)
            server.enqueue(MockResponse().setResponseCode(500))
            assertEquals(
                FailureKind.HTTP,
                (repo.search("Berlin") as SearchOutcome.Failed).failure.kind,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun forecastRequestContract() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(fixture()))
            val remote =
                OpenMeteoRemote(
                    ApiFactory.retrofit(server.url("/").toString(), ApiFactory.client())
                        .create(ForecastService::class.java),
                    clock,
                )
            assertEquals(2, remote.fetch(city).size)
            val url = server.takeRequest().requestUrl!!
            mapOf(
                    "latitude" to "52.52",
                    "longitude" to "13.41",
                    "timezone" to "Europe/Berlin",
                    "forecast_days" to "7",
                    "timeformat" to "iso8601",
                    "temperature_unit" to "celsius",
                    "wind_speed_unit" to "kmh",
                    "precipitation_unit" to "mm",
                    "daily" to
                        "temperature_2m_mean,precipitation_sum,wind_speed_10m_max,snowfall_sum",
                    "hourly" to "snow_depth",
                )
                .forEach { (k, v) -> assertEquals(k, v, url.queryParameter(k)) }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun optionalMetadataAndMalformedEntries() {
        assertTrue(mapCities(GeocodingDto()).isEmpty())
        assertTrue(mapCities(ApiFactory.json.decodeFromString("""{"results":[]}""")).isEmpty())
        assertThrows(DataFailure::class.java) {
            mapCities(
                ApiFactory.json.decodeFromString(
                    """{"results":[{"id":1,"name":"x","latitude":91,"longitude":0,"timezone":"UTC"}]}"""
                )
            )
        }
    }

    @Test
    fun normalizationPartialMissingAndSnowUnits() {
        val date = LocalDate.of(2026, 10, 7)
        assertEquals(40.0, map(fixture())[date]!!.snowDepthCm!!, 0.0)
        assertNull(map(fixture())[date.plusDays(1)]!!.temperatureC)
        assertNull(
            map(fixture().replace("\"snow_depth\":\"m\"", "\"snow_depth\":\"ft\""))[date]!!
                .snowDepthCm
        )
        assertNull(map(fixture().replace("\"hourly\":{", "\"omitted\":{"))[date]!!.snowDepthCm)
        assertNull(map(fixture().replace("2026-10-07T00:00", "nonsense"))[date]!!.snowDepthCm)
        assertNull(
            map(fixture().replace("\"snow_depth\":\"m\"", "\"snow_depth\":null"))[date]!!
                .snowDepthCm
        )
        assertNull(
            map(fixture().replace("\"snow_depth\":\"m\"", "\"snow_depth\":{}"))[date]!!.snowDepthCm
        )
        assertNull(
            map(fixture().replace("[22,null]", "[-0.0,null]"))[date]!!.temperatureC?.takeIf {
                it.toBits() == (-0.0).toBits()
            }
        )
        assertNull(map(fixture().replace("[0,2]", "[-1,2]"))[date]!!.precipitationMm)
    }

    @Test
    fun rejectsBadStructureUnitsTimezoneAndExpiredData() {
        listOf(
                fixture().replace("[22,null]", "[22]"),
                fixture().replace("°C", "°F"),
                fixture().replace("Europe/Berlin", "UTC"),
                fixture().replace("2026-10-08\"", "2026-10-07\""),
                fixture().replace("2026-10-07\"", "not-a-date\""),
                fixture().replace("2026-10-07", "2025-10-07").replace("2026-10-08", "2025-10-08"),
            )
            .forEach { body -> assertThrows(DataFailure::class.java) { map(body) } }
    }

    @Test
    fun transportFailuresAndCancellation() {
        assertEquals(FailureKind.NETWORK, failureOf(java.io.IOException("offline"), clock).kind)
        assertEquals(FailureKind.TIMEOUT, failureOf(java.net.SocketTimeoutException(), clock).kind)
        assertThrows(CancellationException::class.java) {
            failureOf(CancellationException(), clock)
        }
        val client = ApiFactory.client()
        assertEquals(10000, client.connectTimeoutMillis)
        assertEquals(15000, client.readTimeoutMillis)
        assertEquals(20000, client.callTimeoutMillis)
    }

    @Test
    fun transportCanRecoverAnUnreachableAddressWithoutApplicationRetries() = runTest {
        val server = MockWebServer()
        server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        try {
            server.enqueue(MockResponse().setBody("{}"))
            val client =
                ApiFactory.client()
                    .newBuilder()
                    .dns(
                        object : okhttp3.Dns {
                            override fun lookup(hostname: String) =
                                listOf(
                                    java.net.InetAddress.getByName("127.0.0.2"),
                                    java.net.InetAddress.getByName("127.0.0.1"),
                                )
                        }
                    )
                    .build()
            val service =
                ApiFactory.retrofit(
                        server.url("/").newBuilder().host("weather.test").build().toString(),
                        client,
                    )
                    .create(GeocodingService::class.java)
            assertTrue(
                RemoteCityRepository(service, clock).search("Berlin") is SearchOutcome.Success
            )
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun repeatedFallBackHourlyLabelsRemainSeparateSamplesAndDailyArraysMayBeAbsent() {
        val dto = ApiFactory.json.decodeFromString<ForecastDto>(fixture())
        val fall = LocalDate.of(2026, 11, 1)
        val hours = (listOf(0, 1, 1) + (2..11)).map { "2026-11-01T%02d:00".format(it) }
        val hourly =
            HourlyDto(hours, List(hours.size) { kotlinx.serialization.json.JsonPrimitive(0.2) })
        val response =
            dto.copy(
                timezone = "America/New_York",
                daily =
                    dto.daily!!.copy(
                        time = listOf(fall.toString(), fall.plusDays(1).toString()),
                        precipitation_sum = null,
                    ),
                hourly = ApiFactory.json.encodeToJsonElement(HourlyDto.serializer(), hourly),
            )
        val result =
            mapForecast(
                response,
                city.copy(timezone = "America/New_York"),
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC),
            )
        assertEquals(25, result[fall]!!.depthExpectedSamples)
        assertEquals(13, result[fall]!!.depthValidSamples)
        assertEquals(20.0, result[fall]!!.snowDepthCm!!, 0.0)
        assertNull(result[fall]!!.precipitationMm)
    }
}
