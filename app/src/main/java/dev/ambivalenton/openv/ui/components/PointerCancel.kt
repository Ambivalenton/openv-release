// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.ui.input.pointer.PointerInputChange

/**
 * Compose ends a touch that Android cancels (a system gesture took it over, e.g. the swipe
 * up to the recent apps that starts at the bottom edge) with a release that is already consumed. A
 * real lift of the finger is not consumed before the gesture sees it. Custom gesture loops must treat
 * this as an abort: no tap, no drop, no commit.
 */
internal val PointerInputChange.systemCancelled: Boolean
    get() = !pressed && previousPressed && isConsumed
