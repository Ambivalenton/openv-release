// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.location

import dev.ambivalenton.openv.data.model.DevicePresence
import dev.ambivalenton.openv.data.model.Location

/** Android-free location port; the existing provider shares its short-lived fix across screens. */
interface CurrentPositionSource {
    fun hasPermission(): Boolean
    fun locationEnabled():Boolean = true

    val lastPresence: DevicePresence?
    val continuousTracking:Boolean get()=false
    val lastActivity:dev.ambivalenton.openv.data.model.DeviceActivityEvidence? get()=null
    val lastMotion: dev.ambivalenton.openv.data.model.DeviceMotion? get()=null

    suspend fun current(): Location

    /** Explicit resync; adapters without a platform cache can use their regular measurement. */
    suspend fun currentFresh(): Location = current()
}
