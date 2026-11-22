// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MANUAL_REFRESH_CONFIRMATION_MS = 1_000L

/** M15: a refresh that never starts loading ends its confirmation after this time. */
const val MANUAL_REFRESH_START_GRACE_MS = 1_500L
/** M15: upper bound of a confirmation that follows a real request. */
const val MANUAL_REFRESH_MAX_MS = 30_000L

/**
 * UI-thread controller. Repository loading can block a gesture, never start its confirmation.
 * M15: an accepted pull is confirmed at once and stays visible until the request it started has
 * actually ended ([loadingChanged]); a fixed timer no longer ends it while the request still runs.
 */
class ManualPullRefreshController(private val scope: CoroutineScope) {
    private val visible = MutableStateFlow(false)
    val confirmationVisible: StateFlow<Boolean> = visible.asStateFlow()
    private var timer: Job? = null
    private var generation = 0L
    private var sawLoading = false

    fun confirmGesture(blocked: Boolean = false): Boolean {
        if (blocked || visible.value || !scope.isActive) return false
        val token = ++generation
        visible.value = true
        sawLoading = false
        timer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Without a request in flight shortly after the pull, nothing is being refreshed.
                delay(MANUAL_REFRESH_START_GRACE_MS)
                if (!sawLoading) return@launch
                delay(MANUAL_REFRESH_MAX_MS - MANUAL_REFRESH_START_GRACE_MS)
            } finally {
                if (token == generation) {
                    visible.value = false
                    timer = null
                }
            }
        }
        return true
    }

    /** M15: the confirmation follows the real request: it ends when loading ends. */
    fun loadingChanged(loading: Boolean) {
        if (!visible.value) return
        if (loading) { sawLoading = true; return }
        if (sawLoading) cancel()
    }

    fun cancel() {
        generation++
        timer?.cancel()
        timer = null
        visible.value = false
    }
}
