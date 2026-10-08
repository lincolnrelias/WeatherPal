package com.example.weatherpal.data.remote

import com.example.weatherpal.domain.model.AppFailure
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One instance per provider host, retained for the repository's application lifetime. */
class RequestCooldown(private val clock: Clock) {
    private val lock = Mutex()
    private var failure: AppFailure? = null

    suspend fun currentFailure(): AppFailure? =
        lock.withLock {
            failure?.takeIf { it.retryAt?.isAfter(clock.instant()) == true }.also { failure = it }
        }

    suspend fun record(value: AppFailure) =
        lock.withLock {
            val deadline = value.retryAt
            if (
                deadline != null &&
                    deadline.isAfter(clock.instant()) &&
                    (failure?.retryAt?.isAfter(deadline) != true)
            ) {
                failure = value
            }
        }
}
