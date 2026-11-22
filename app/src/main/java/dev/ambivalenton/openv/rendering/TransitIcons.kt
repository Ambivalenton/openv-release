// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.rendering

import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.LineType

/** One vector mapping for the route timeline and the exported card. */
fun LineType.iconResource(): Int =
    when (this) {
        LineType.BUS -> R.drawable.ic_bus
        LineType.TRAM -> R.drawable.ic_tram
        LineType.SUBURBAN -> R.drawable.ic_suburban
        LineType.SUBWAY -> R.drawable.ic_subway
        LineType.FERRY -> R.drawable.ic_ferry
        LineType.WALK -> R.drawable.ic_walk
        else -> R.drawable.ic_train
    }
