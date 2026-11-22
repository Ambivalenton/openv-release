// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Re-read the zone and wall clock on resume and at minute boundaries, rather than at composition
 * only.
 */
@Composable
fun rememberWallClock(tickMillis:Long=60_000): ZonedDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val interval=tickMillis.coerceAtLeast(1000)
    return produceState(ZonedDateTime.now(), lifecycle, interval) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    value = ZonedDateTime.now()
                    delay((interval - System.currentTimeMillis() % interval).coerceAtLeast(1))
                }
            }
        }
        .value
}
