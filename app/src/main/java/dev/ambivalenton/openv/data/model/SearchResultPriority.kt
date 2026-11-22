// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable

@Serializable enum class SearchResultPriority(private val titleKey:String) {
    STOPS_FIRST("Haltestellen zuerst"), ADDRESSES_FIRST("Adressen zuerst");
    val title get()=tr(titleKey)
}

/** Stable partition: relevance inside each category and exact-coordinate queries stay intact. */
fun rankPlaceResults(rows:List<Location>,priority:SearchResultPriority):List<Location> {
    val (stops,addresses)=rows.partition {it.kind==LocationKind.STOP}
    return if(priority==SearchResultPriority.STOPS_FIRST) stops+addresses else addresses+stops
}

@Serializable enum class SearchResultGroup(private val titleKey:String) {
    FAVORITES("Favoriten"),LOCAL_STOPS("Lokale Haltestellen"),EXACT_PLACES("Genaue Adressen und Orte"),MORE_PLACES("Weitere passende Vorschläge"),REMOTE_STOPS("Zusätzliche Online-Haltestellen");
    val title get()=tr(titleKey)
}
val defaultSearchResultOrder=SearchResultGroup.entries.toList()
fun normalizedSearchOrder(order:List<SearchResultGroup>):List<SearchResultGroup> {
    // Existing four-group preferences gain favorites at the top; later custom ordering survives.
    val migrated=if(SearchResultGroup.FAVORITES in order)order else listOf(SearchResultGroup.FAVORITES)+order
    return (migrated+defaultSearchResultOrder).distinct().take(SearchResultGroup.entries.size)
}
fun movedSearchGroup(order:List<SearchResultGroup>,group:SearchResultGroup,direction:Int):List<SearchResultGroup> {
    val r=normalizedSearchOrder(order).toMutableList();val i=r.indexOf(group);val to=(i+direction.coerceIn(-1,1)).coerceIn(0,r.lastIndex)
    if(i!=to){r.removeAt(i);r.add(to,group)};return r
}
