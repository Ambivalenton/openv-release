// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import dev.ambivalenton.openv.data.model.Location
import kotlinx.coroutines.flow.Flow

data class PlaceSearchGateway(val local:(String)->List<Location>,val updates:(String)->Flow<List<Location>>,val startsImmediately:Boolean=false)
val LocalPlaceSearchGateway=staticCompositionLocalOf<PlaceSearchGateway?> {null}
const val PLACE_SEARCH_DEBOUNCE_MILLIS=150L
