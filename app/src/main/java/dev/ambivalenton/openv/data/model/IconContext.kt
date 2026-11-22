// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.serviceLanguage
import kotlin.random.Random

internal data class IconText(val german: String,val english: String) {
    val text: String get()=if(serviceLanguage()=="de") german else english
}
/** [routes]: full sentences with {origin}, {target}, {dep} and {arr}; [from]: sentences for a start
 * favourite with that icon (network plan rows), e.g. WORK → HOME "Feierabend …". */
internal data class IconCopy(val arrivals: List<IconText>,val leads: List<IconText>,
    val routes: List<IconText> = emptyList(),val from: Map<FavoriteIcon,List<IconText>> = emptyMap())
private fun canonicalIcon(icon: FavoriteIcon?)=if(icon==FavoriteIcon.HEART) FavoriteIcon.FAVORITE else icon ?: FavoriteIcon.PLACE
/** A category without copy falls back to the neutral place texts. An earlier version crashed the overview
 * here: the selectable "Haltestelle" icon (STOP) had no entry and getValue() threw while drawing. */
private fun copyFor(icon: FavoriteIcon?)=generatedIconCopy[canonicalIcon(icon)] ?: generatedIconCopy.getValue(FavoriteIcon.PLACE)

fun iconArrivalMessages(icon: FavoriteIcon?): List<String> = copyFor(icon).arrivals.map { it.text }
fun iconJourneyLead(icon: FavoriteIcon?,variant: Int): String = copyFor(icon).leads.let {
    it[Math.floorMod(variant,it.size)].text
}

/**
 * Full overview sentence template for a complete row (all four values shown), or null when the
 * generic wording should be used. A network row knows its start favourite: a matching pair
 * (work → home, home → school, …) wins over the destination's own sentences; a plain place as
 * destination gets neutral two-place sentences instead of the old "Pendelweg".
 */
fun iconRouteTemplate(target: FavoriteIcon?,origin: FavoriteIcon?,network: Boolean,variant: Int): String? {
    val copy=copyFor(target)
    val pool=(if(network && origin!=null) copy.from[canonicalIcon(origin)] else null)?.takeIf { it.isNotEmpty() }
        ?: copy.routes.takeIf { it.isNotEmpty() && canonicalIcon(target)!=FavoriteIcon.PLACE }
        ?: generatedNetworkRoutes.takeIf { network }
        ?: return null
    return pool[Math.floorMod(variant,pool.size)].text
}

/** Separate shuffle bags per language/category; no repeats even across bag boundaries. */
class IconArrivalPicker(private val random: Random=Random.Default) {
    private val decks=mutableMapOf<Pair<String,FavoriteIcon>,TransitHeaderDeck>()
    @Synchronized fun next(icon: FavoriteIcon?): String = decks.getOrPut(serviceLanguage() to canonicalIcon(icon)) {
        TransitHeaderDeck(iconArrivalMessages(icon),random)
    }.next()
}
