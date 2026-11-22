// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/** Persisted with the settings so process death never loses the last successful board. */
@Serializable
data class DepartureBoardState(
    val stop: Location? = null,
    val departures: List<Departure> = emptyList(),
    val fetchedAt: Long = 0,
    val presenceAt: Long = 0,
    val configurationKey: String = "",
    val error: String? = null,
    /** H05: the classified cause of [error] (FailureKind name), so the board can say what helps. */
    val errorKind: String? = null,
    /** Board choice (nearest place) this result belongs to; see boardChoiceSignature. */
    val choice: String = "",
    val requestedRows:Int=8,
)
