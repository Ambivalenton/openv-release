// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import kotlinx.coroutines.TimeoutCancellationException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * H05: why a request failed. Search, results and departures map the same cause to the same message
 * and the same next step, instead of passing raw exception texts through.
 */
enum class FailureKind {
    /** The device has no usable internet connection (flight mode, no network, no DNS). */
    OFFLINE,
    /** The service did not answer within the time limit. */
    TIMEOUT,
    /** HTTP 429 or a pause the service asked for (Retry-After). */
    RATE_LIMITED,
    /** HTTP 5xx, refused connection: the service does not answer right now. */
    UNAVAILABLE,
    /** The answer could not be read or was incomplete. */
    INVALID_RESPONSE,
    /** No start is known for this journey. */
    MISSING_START,
    /** The provider has no data for this place or route: the evidence comes from its own answer. */
    NO_COVERAGE,
    /** The provider answered correctly but has no matching connection for this time (valid empty answer). */
    NO_CONNECTION,
    OTHER,
}

/** One classified failure. [retryAt] is the earliest sensible retry (429/503 with Retry-After). */
data class TransitFailure(val kind: FailureKind, val detail: String? = null, val retryAt: Long? = null) {
    /** Seconds until [retryAt], at least one while a pause is active; null without a pause. */
    fun waitSeconds(now: Long): Long? = retryAt?.let { ((it - now + 999) / 1000).takeIf { s -> s > 0 } }
}

/** Classifies any failure of a transit, geocoding or departure request. */
fun transitFailure(error: Throwable): TransitFailure {
    var cause: Throwable? = error
    var depth = 0
    while (cause != null && depth < 6) {
        when (cause) {
            is TransitException -> return TransitFailure(cause.kind, cause.message, cause.retryAt)
            is TimeoutCancellationException, is SocketTimeoutException -> return TransitFailure(FailureKind.TIMEOUT, error.message)
            is UnknownHostException, is NoRouteToHostException -> return TransitFailure(FailureKind.OFFLINE, error.message)
            is ConnectException -> return TransitFailure(
                if (cause.message.orEmpty().contains("ENETUNREACH") || cause.message.orEmpty().contains("unreachable", true))
                    FailureKind.OFFLINE else FailureKind.UNAVAILABLE, error.message)
            // OkHttp's call timeout is an InterruptedIOException("timeout").
            is InterruptedIOException -> if (cause.message.orEmpty().contains("timeout", true)) return TransitFailure(FailureKind.TIMEOUT, error.message)
        }
        cause = cause.cause
        depth++
    }
    return TransitFailure(FailureKind.OTHER, error.message)
}

/** Classifies an OkHttp transport failure; [online] is the device's own view, if known. */
internal fun transportFailureKind(error: Throwable, online: Boolean?): FailureKind =
    if (online == false) FailureKind.OFFLINE
    else transitFailure(error).kind.let { if (it == FailureKind.OTHER) FailureKind.UNAVAILABLE else it }

/** HTTP status → cause. 429 is a rate limit; any 5xx (also 503 without Retry-After) means "not answering". */
internal fun httpFailureKind(code: Int): FailureKind = when {
    code == 429 -> FailureKind.RATE_LIMITED
    code in 500..599 -> FailureKind.UNAVAILABLE
    else -> FailureKind.OTHER
}
