// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable
import dev.ambivalenton.openv.localization.tr
import kotlin.math.abs

@Serializable enum class ChartLongPressAction(val titleKey:String) {
    NOTHING("Nichts"), SHARE(""), SAVE("Verbindung für später speichern"), PIN("Verbindung anpinnen"), WAYPOINT("Zwischenstopp hinzufügen");
    val title get()=tr(if (this == SHARE) journeyActionTitle else titleKey)
}

/**
 * What holding a favourite in the network plan does (live mode, not while editing).
 * [MOVE] is the default: the plan switches into edit mode and the held favourite follows the finger.
 * [OVERVIEW] pins up to five favourites to the overview (build 81; see [overviewFocusRows]).
 */
@Serializable enum class NetworkHoldAction(val titleKey:String,private val descriptionKey:String) {
    NOTHING("Nichts tun","Gedrückthalten hat keine Wirkung. Bearbeiten geht weiter über den Stift."),
    MOVE("Favoriten-Symbol frei bewegen","Der Netzplan wechselt in die Bearbeitung und der Favorit folgt deinem Finger auf ein freies Feld."),
    LOCATION("Als aktuellen Standort festlegen","Die ganze App rechnet ab diesem Favoriten. Erneut gedrückt halten hebt die Festlegung auf."),
    PIN("Verbindung anpinnen","Die nächste Verbindung zu diesem Favoriten wird gesucht und direkt angepinnt."),
    OVERVIEW("In Übersicht anzeigen","Gedrückt halten heftet bis zu fünf Favoriten an die Übersicht, erneut halten löst sie. Die Übersicht zeigt dann nur zeitlich hervorgehobene und angeheftete Favoriten. Ohne beides zeigt sie alle Ziele, aber keine Umstiegspunkte.");
    val title get()=tr(titleKey)
    val description get()=tr(descriptionKey)
}

/** The hold action in effect: the Planermodus keeps its own choice and has no overview to pin to. */
val AppSettings.holdAction: NetworkHoldAction get() =
    if (plannerMode) plannerHoldAction.takeIf { it != NetworkHoldAction.OVERVIEW } ?: NetworkHoldAction.LOCATION else networkHoldAction

/** The choices offered for holding a favourite; the Planermodus offers no "In Übersicht anzeigen". */
fun networkHoldChoices(planner: Boolean): List<NetworkHoldAction> =
    if (planner) NetworkHoldAction.entries.filter { it != NetworkHoldAction.OVERVIEW } else NetworkHoldAction.entries

/** In the Planermodus, "Standort festlegen" puts the favourite into the start field instead. */
fun NetworkHoldAction.titleFor(planner: Boolean): String =
    if (planner && this == NetworkHoldAction.LOCATION) tr("Als Startpunkt setzen") else title
fun NetworkHoldAction.descriptionFor(planner: Boolean): String =
    if (planner && this == NetworkHoldAction.LOCATION) tr("Der Favorit wird oben als Start eingetragen. Die Suche beginnt dann dort.") else description

/** Start includes the approach walk; exactly five minutes is still too early. */
fun alternativeSuggestionWindow(session:PinnedJourneySession,now:Long):Boolean {
    if(session.trip.segments.isEmpty())return false
    val start=session.trip.segments.first().effectiveDeparture()
    // A matching GPS route must not activate a separately pinned later departure. The caller
    // stops prompts on confirmed arrival; an obsolete arrival forecast must not stop recovery.
    return start-now<5*60_000L
}

data class AlternativeComparison(val current:Trip,val offered:Trip) {
    val departureChanged get()=current.firstBoardingTime()/60_000!=offered.firstBoardingTime()/60_000
    val arrivalChanged get()=current.effectiveArrival()/60_000!=offered.effectiveArrival()/60_000
    private fun Trip.boardingStop()=segments.firstOrNull {!it.isWalk}?.origin?:segments.firstOrNull()?.origin
    val originChanged get()=current.boardingStop()?.let {a->offered.boardingStop()?.let {b->!sameLocation(a,b)}}?:false
    val currentOrigin get()=current.boardingStop()?.name.orEmpty()
    val offeredOrigin get()=offered.boardingStop()?.name.orEmpty()
    val transfersChanged get()=current.transfers!=offered.transfers
    private fun Trip.duration()=(effectiveArrival()-startTime).coerceAtLeast(0)
    val durationChanged get()=abs(current.duration()-offered.duration())>5*60_000L
    val currentMinutes get()=(current.duration()+59_999)/60_000
    val offeredMinutes get()=(offered.duration()+59_999)/60_000
}
