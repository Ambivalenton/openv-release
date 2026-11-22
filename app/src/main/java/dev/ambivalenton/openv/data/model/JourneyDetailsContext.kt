// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** Opening source is explicit; a one-item comparison still uses its normal numbering. */
sealed interface JourneyDetailsContext {
    data object Comparison : JourneyDetailsContext
    data class Overview(val favoriteName: String) : JourneyDetailsContext
}

/** Query, displayed trip and title source travel together and are dismissed together. */
data class JourneyPreviewRequest(
    val query: JourneyQuery,
    val trip: Trip,
    val detailsContext: JourneyDetailsContext = JourneyDetailsContext.Comparison,
    val comparisons:List<Trip> = emptyList(),
)

fun journeyDetailsTitle(context: JourneyDetailsContext, page: Int, count: Int): String {
    val name=(context as? JourneyDetailsContext.Overview)?.favoriteName?.trim().orEmpty()
    if(name.isNotEmpty()) return tr("Verbindung nach %1\$s",name)
    val total=count.coerceAtLeast(1)
    return tr("Verbindung %1\$s/%2\$s",(page+1).coerceIn(1,total),total)
}
