// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import dev.ambivalenton.openv.data.model.*

data class WaypointRoutingGateway(val route:suspend (JourneyQuery)->Trip)
val LocalWaypointRoutingGateway=staticCompositionLocalOf<WaypointRoutingGateway?> {null}
