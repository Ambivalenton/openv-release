// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

/**
 * Fixed Material tonal pairs. System/Monet colors remain delegated to MaterialTheme/GlanceTheme.
 */
data class TonalPalette(
    val lightContainer: Long,
    val darkContainer: Long,
    val lightContent: Long,
    val darkContent: Long,
)

fun widgetPalette(accent: Accent): TonalPalette? =
    when (accent) {
        Accent.DYNAMIC -> null
        Accent.TEAL -> TonalPalette(0xFF9EF2E8, 0xFF00504B, 0xFF00201E, 0xFF9EF2E8)
        Accent.BLUE -> TonalPalette(0xFFD6E3FF, 0xFF224876, 0xFF001B3D, 0xFFD6E3FF)
        Accent.VIOLET -> TonalPalette(0xFFEADDFF, 0xFF533A75, 0xFF250A48, 0xFFEADDFF)
    }
