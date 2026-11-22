// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui

import dev.ambivalenton.openv.data.model.forDestination
import dev.ambivalenton.openv.data.model.JourneyDetailsContext
import dev.ambivalenton.openv.data.model.JourneyPreviewRequest

/** Use the saved favourite label, not the station/provider destination name. */
fun OverviewEntry.journeyPreviewRequest(): JourneyPreviewRequest? {
    val query=this.query ?: return null
    val trip=this.trip ?: return null
    val target=favorite.forDestination(query.destination)
    val name=target.label.trim().ifBlank {target.location.name}
    return JourneyPreviewRequest(query,trip,JourneyDetailsContext.Overview(name))
}
