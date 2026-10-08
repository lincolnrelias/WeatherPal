package com.example.weatherpal.data.remote

import com.example.weatherpal.domain.model.*
import java.io.IOException
import java.io.InterruptedIOException
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class DataFailure(val failure: AppFailure) : Exception(failure.diagnostic)

internal fun malformed(detail: String): Nothing =
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
