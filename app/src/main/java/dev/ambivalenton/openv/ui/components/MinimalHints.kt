// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.ambivalenton.openv.ui.theme.LocalAppSettings

/**
 * Hints, explanations and info texts of the screens. The minimalist mode hides all of them.
 * Errors, real-time and disruption notices, missing data and required source notes always stay.
 */
val hintsShown: Boolean
    @Composable @ReadOnlyComposable get() = !LocalAppSettings.current.minimalistMode
