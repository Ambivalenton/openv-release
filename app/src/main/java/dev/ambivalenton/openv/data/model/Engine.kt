// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/** Stored identity of the timetable service; see [TimetableServices]. */
@Serializable
enum class Engine { VRR, DELFI }
