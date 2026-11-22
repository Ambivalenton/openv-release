// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/** Separate from the editorial shuffle bag: tips stop after a week, sayings never disappear. */
val appUsageTips:List<JourneyContent> = listOf(
    "Halte einen Suchtreffer gedrückt, um ihn direkt als Favorit anzulegen." to "Hold a search result to add it directly as a favourite.",
    "Tippe kurz auf den Pin einer Verbindung, um den Reisebegleiter zu starten." to "Tap a journey's pin to start the travel assistant.",
    "Halte den Pin einer Verbindung gedrückt, um sie für später vorzumerken. Die Begleitung startet dabei noch nicht." to "Hold a journey's pin to save it for later without starting guidance.",
    "Tippe auf eine vorgemerkte Verbindung, um direkt ihren Zeitstrahl mit den einzelnen Abschnitten zu öffnen." to "Tap a saved journey to open its detailed timeline directly.",
    "Halte eine vorgemerkte Verbindung gedrückt, um sie direkt anzupinnen." to "Hold a saved journey to pin it directly.",
    "Unter Modi & Verhalten bestimmst du, was langes Drücken auf einen Balken macht: nichts, teilen, vormerken, anpinnen oder einen Zwischenstopp hinzufügen." to "Modes & behaviour lets you choose what holding a journey bar does: nothing, share, save for later, pin or add a waypoint.",
    "Der Schalter für alternative Fahrten steuert nur automatische Pop-ups. Im angepinnten Zeitstrahl bleiben Alternativen immer zugänglich." to "The alternative journeys switch only controls automatic pop-ups. Alternatives remain accessible in the pinned timeline.",
    "Ein automatischer Alternativvergleich hilft erst kurz vor dem Start und unterwegs. Eine für später angepinnte Fahrt löst ihn nicht sofort aus." to "Automatic alternative comparisons help shortly before departure and en route. Pinning a journey for later does not immediately trigger one.",
    "Du kannst deinen Startort selbst wählen. Eine Verbindungssuche ist auch ohne Standortfreigabe möglich." to "You can choose your own origin. Journey search also works without location permission.",
    "Wähle bei der Suche zwischen Abfahrt und Ankunft, wenn du zu einer bestimmten Zeit losfahren oder ankommen möchtest." to "Choose departure or arrival when searching to leave or arrive at a particular time.",
    "Mit den Verkehrsmittelfiltern passt du die Suche an deinen Weg an, zum Beispiel nur mit Bus und Bahn vor Ort." to "Use transport filters to tailor a search, for example to local buses and trains.",
    "Fernverkehr lässt sich in den Einstellungen gesondert berücksichtigen. Prüfe vor der Fahrt, ob dein Ticket dafür gilt." to "Long-distance services can be included separately in settings. Check whether your ticket covers them before travelling.",
    "Ein Favorit darf einen eigenen Namen und ein Symbol haben. So erkennst du Zuhause, Arbeit oder andere Ziele schneller." to "Give a favourite its own name and icon to recognise home, work and other destinations faster.",
    "Mit „Zeitabhängiger Standort“ gilt ein Favorit zu festen Zeiten als dein Standort, etwa die Arbeit werktags von 8 bis 16 Uhr. Das hilft ohne Standortfreigabe oder ohne GPS." to "With “Time-dependent location” a favourite counts as your location at fixed times, for example work on weekdays from 8 to 4. This helps without location access or GPS.",
    "Ist ein Favorit gerade zeitlich hervorgehoben, zeigt die Übersicht nur die hervorgehobenen Verbindungen. Sonst bleibt alles wie gewohnt." to "While a favourite is highlighted by its time rule, the overview shows only the highlighted connections. Otherwise everything stays as usual.",
    "Die Option zum Ausblenden naher Favoriten nutzt deinen tatsächlichen Gerätestandort, nicht einen manuell eingegebenen Startort." to "Hiding nearby favourites uses your actual device location, not a manually entered origin.",
    "Der Minimalismus-Modus blendet Infotext und Suchchroniken aus. Unter Abfahrten bekommt die Anzeigetafel den verfügbaren Bildschirm." to "Minimalism mode hides the information text and search histories, giving the departure board the available screen space.",
    "Unter Schrift & Lesbarkeit zeigt dir die Schriftart-Vorschau schon vor dem Öffnen die aktuell gewählte Schrift." to "The font preview in Font & readability shows the current typeface before you open the picker.",
    "Schriftgröße und Kontrast kannst du unter Schrift & Lesbarkeit anpassen, bis die App für dich angenehm zu lesen ist." to "Adjust text size and contrast in Font & readability until the app feels comfortable to read.",
    "App-Design und Anzeigetafel-Design lassen sich unabhängig auswählen. Du kannst also auch verschiedene Stile kombinieren." to "Choose the app and departure board designs independently to mix different styles.",
    "Einige App-Designs nutzen transparente Flächen. Probiere sie mit deinem bevorzugten Hintergrund und Kontrast aus." to "Some app designs use translucent surfaces. Try them with your preferred background and contrast.",
    "Du kannst eine feste Haltestelle für die Anzeigetafel wählen, statt sie vom aktuellen Standort abhängig zu machen." to "Choose a fixed stop for the departure board instead of making it depend on your current location.",
    "App und Widget greifen für die Anzeigetafel auf dieselbe gespeicherte Haltestellenauswahl zurück." to "The app and widget use the same saved stop selection for the departure board.",
    "Tippe auf einen Spruch oder Fakt, um einen anderen Text zu lesen." to "Tap a saying or fact to read a different text.",
    "Halte einen Spruch oder Fakt drei Sekunden gedrückt, um deine Textchronik mit bereits gesehenen Texten zu öffnen." to "Hold a saying or fact for three seconds to open your history of texts you have already seen.",
    "Die Rahmen für Sprüche und Fakten kannst du selbst wählen: von einer feinen Linie bis zu einer Karte im aktuellen App-Design." to "Choose a frame for sayings and facts, from a fine line to a card matching the current app design.",
    "Im Ruhemodus werden verspielte Animationen und dekorative Muster reduziert. Du findest ihn unter Modi & Verhalten." to "Quiet mode reduces playful animations and decorative patterns. Find it in Modes & behaviour.",
    "Fußabdrücke und ihre Größe kannst du anpassen. Rollende Spuren und verschiedene Gangarten stehen ebenfalls zur Auswahl." to "Customise footprints and their size. Rolling tracks and different gaits are available too.",
    "Der Energiesparmodus reduziert Hintergrundarbeit. Schalte ihn ein, wenn dir unterwegs die Akkulaufzeit besonders wichtig ist." to "Power saving mode reduces background work. Enable it when battery life matters most on your journey.",
    "Wetter an deinem Ziel ist eine freiwillige Ergänzung. Du kannst es in den Einstellungen der Übersicht ein- oder ausschalten." to "Destination weather is optional. Turn it on or off in the overview settings.",
    "Mit Kompakt, Alltag und Alle Details stellst du ein, wie ausführlich deine Übersicht Verbindungen beschreibt." to "Compact, Everyday and All details control how much information the overview includes in its journey descriptions.",
    "Ein Zeitpuffer in der Übersicht lässt die Suche etwas später starten, damit du dich vor dem Aufbruch nicht hetzen musst." to "A time buffer in the overview starts the search a little later, giving you time to get ready before leaving.",
    "Im Zeitstrahl kannst du zwischen Verbindungen nach links und rechts wechseln. Die kleinen Pfeile erinnern an die Wischgeste." to "Swipe left or right in the timeline to switch between journeys. The small arrows remind you of this gesture.",
    "Ein kurzer Tipp auf eine Verbindung in der Übersicht öffnet genau diese Fahrt mit ihren Abschnitten und Zeiten." to "Tap a connection in the overview to open that exact journey with its legs and times.",
    "Standardmäßig zeigt die Suche lokale Haltestellen zuerst. Unter Fahrt & Navigation kannst du die Treffergruppen selbst nach oben oder unten verschieben." to "Search shows local stops first by default. In Journey & navigation, move result groups up or down to choose your own order.",
    "Achte bei gespeicherten Abfahrten auf den angegebenen Stand. Eine frühere Anzeige ersetzt keine aktuelle Echtzeitmeldung." to "Check the update time of saved departures. A previous display does not replace current real-time information.",
    "Du kannst Standort- und Benachrichtigungsfreigaben später ändern. In der Ersteinrichtung darfst du freiwillige Schritte überspringen." to "You can change location and notification permissions later. Optional first-run steps can be skipped.",
).mapIndexed {i,(de,en)->JourneyContent("app-tip-%02d".format(i+1),JourneyContentKind.TIP,de,en,null,"Öpen-V",null)}

@Serializable data class UsageTipProgress(val startedAt:Long?=null,val switchesSinceTip:Int=0,val nextTipIndex:Int=0)
data class UsageTipStep(val progress:UsageTipProgress,val tip:JourneyContent?=null)
val usageTipCadence=listOf(3,5,7,9,11,13,15)
const val USAGE_TIP_DAY=24*60*60*1_000L

fun advanceUsageTips(previous:UsageTipProgress,now:Long):UsageTipStep {
    val progress=previous.copy(startedAt=previous.startedAt ?: now)
    val elapsed=(now-progress.startedAt!!).coerceAtLeast(0)
    if(elapsed>=7*USAGE_TIP_DAY)return UsageTipStep(progress)
    val interval=usageTipCadence[(elapsed/USAGE_TIP_DAY).toInt()]
    val count=progress.switchesSinceTip.coerceIn(0,15)+1
    if(count<interval)return UsageTipStep(progress.copy(switchesSinceTip=count))
    val index=Math.floorMod(progress.nextTipIndex,appUsageTips.size)
    return UsageTipStep(progress.copy(switchesSinceTip=0,nextTipIndex=(index+1)%appUsageTips.size),appUsageTips[index])
}
