// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable

@Serializable enum class WalkingNavigationMode(val titleKey: String,val detailKey: String) {
    OPENSTREETMAP("OpenStreetMap Navigation","Fußweg in OpenStreetMap öffnen · Standard"),
    GOOGLE_NAVIGATION("Google Maps Navigation direkt starten","Fußnavigation vom aktuellen Standort starten"),
    GOOGLE_LOCATION("Google Maps Fußweg anzeigen","Fußroute auf der Karte anzeigen, ohne Navigation zu starten");
    val title get()=tr(titleKey)
    val description get()=tr(detailKey)
    val actionLabel get()=when(this) {
        OPENSTREETMAP->tr("Fußweg in OpenStreetMap öffnen")
        GOOGLE_NAVIGATION->tr("Google Maps Navigation direkt starten")
        GOOGLE_LOCATION->tr("Google Maps Fußweg anzeigen")
    }
}
