package com.example.weatherpal.ui

import com.example.weatherpal.data.MutableClock
import com.example.weatherpal.domain.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test
    fun unavailableMeasurementsStayDistinctFromZeroAndUseDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Unavailable", number(null, "mm"))
            assertEquals("0.0 mm", number(0.0, "mm"))
            Locale.setDefault(Locale.GERMANY)
            assertEquals("12,5 °C", number(12.5, "°C"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun snowFallbackExplainsItsLimitAndFailuresKeepDiagnosticsPrivate() {
        val explanation = reasonText(Reason(ReasonCode.SNOWFALL, 10.0, 45), Activity.SKIING)
        assertTrue(explanation.contains("depth is unavailable"))
        assertTrue(explanation.contains("cannot establish a snow base"))
        val failure = AppFailure(FailureKind.STORAGE, "private database path")
        assertTrue(failure.message().contains("retry"))
        assertFalse(failure.message().contains("private database path"))
    }

    @Test
    fun countdownRoundsUpUntilExactDeadlineAndHandlesClockChanges() {
        val clock = MutableClock()
        val deadline = clock.now.plusSeconds(60)
        val failure = AppFailure(FailureKind.RATE_LIMITED, retryAt = deadline)
        assertEquals(60L, remainingRetrySeconds(failure, clock))
        clock.now = deadline.minusNanos(1)
        assertEquals(1L, remainingRetrySeconds(failure, clock))
        clock.now = deadline
        assertEquals(0L, remainingRetrySeconds(failure, clock))
        clock.now = deadline.plusSeconds(1)
        assertEquals(0L, remainingRetrySeconds(failure, clock))
        clock.now = deadline.minusSeconds(120)
        assertEquals(120L, remainingRetrySeconds(failure, clock))
        assertEquals(0L, remainingRetrySeconds(AppFailure(FailureKind.NETWORK), clock))
    }
}
