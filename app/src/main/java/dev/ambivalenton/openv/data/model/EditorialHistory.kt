// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

@kotlinx.serialization.Serializable
data class SeenEditorialText(val key:String,val contentId:String,val kind:JourneyContentKind,
    val text:String,val language:String,val sourceUrl:String?,val firstShownAt:Long,val lastShownAt:Long,val shownCount:Long)

fun editorialHistoryGroups(rows:List<SeenEditorialText>):Pair<List<SeenEditorialText>,List<SeenEditorialText>> =
    rows.filter {it.kind!=JourneyContentKind.TIP}.sortedByDescending {it.lastShownAt}.partition {it.kind==JourneyContentKind.FACT}

fun editorialContentForText(text:String):JourneyContent? = (journeyContentCatalog.asSequence()+appUsageTips.asSequence()).firstOrNull {
    it.german==text || it.english==text
}
