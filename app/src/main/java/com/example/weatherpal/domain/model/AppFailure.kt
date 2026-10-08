package com.example.weatherpal.domain.model

import java.time.Instant

enum class FailureKind {
    VALIDATION,
    NETWORK,
    TIMEOUT,
    HTTP,
    RATE_LIMITED,
    MALFORMED_RESPONSE,
    UNUSABLE_FORECAST,
    STORAGE,
    CANCELLED,
}

data class AppFailure(
    val kind: FailureKind,
    val diagnostic: String? = null,
    val retryAt: Instant? = null,
)
