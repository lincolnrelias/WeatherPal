package com.example.weatherpal.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.example.weatherpal.domain.model.AppFailure
import com.example.weatherpal.ui.presentation.remainingRetrySeconds
import java.time.Clock
import kotlinx.coroutines.delay

@Composable
fun retrySeconds(failure: AppFailure?, clock: Clock): Long {
    val remaining by
        produceState(remainingRetrySeconds(failure, clock), failure?.retryAt, clock) {
            do {
                value = remainingRetrySeconds(failure, clock)
                if (value > 0) delay(1000)
            } while (value > 0)
        }
    return remaining
}

@Composable
fun RetryButton(failure: AppFailure, clock: Clock, onRetry: () -> Unit) {
    val remaining = retrySeconds(failure, clock)
    TextButton(onClick = onRetry, enabled = remaining == 0L) {
        Text(if (remaining > 0) "Retry in ${remaining}s" else "Retry")
    }
}
