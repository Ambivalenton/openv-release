// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr


/** One forecast hour at the destination. All timestamps are epoch milliseconds (DST safe). */
data class ArrivalWeather(
    val hour: Long,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val weatherCode: Int,
    val precipitationProbability: Int? = null,
    val precipitationMm: Double? = null,
    val windKmh: Double? = null,
    val isDay: Boolean = true,
    val timezone: String = "UTC",
) {
    fun covers(arrival: Long) = arrival >= hour && arrival - hour < 3_600_000
}

enum class ArrivalWeatherMood {
    THUNDER, FREEZING_RAIN, SNOW, HEAVY_RAIN, RAIN, POSSIBLE_RAIN, FOG, WIND, COLD, HOT, SUN, MILD
}

/** Severe conditions take precedence over comfort hints; uncertainty stays explicit. */
fun ArrivalWeather.mood(): ArrivalWeatherMood = when {
    weatherCode in setOf(95, 96, 99) -> ArrivalWeatherMood.THUNDER
    weatherCode in setOf(56, 57, 66, 67) -> ArrivalWeatherMood.FREEZING_RAIN
    weatherCode in setOf(71, 73, 75, 77, 85, 86) -> ArrivalWeatherMood.SNOW
    weatherCode in setOf(65, 82) || (precipitationMm ?: 0.0) >= 3.0 -> ArrivalWeatherMood.HEAVY_RAIN
    weatherCode in setOf(51, 53, 55, 61, 63, 80, 81) -> ArrivalWeatherMood.RAIN
    (precipitationProbability ?: 0) >= 60 -> ArrivalWeatherMood.POSSIBLE_RAIN
    weatherCode in setOf(45, 48) -> ArrivalWeatherMood.FOG
    (windKmh ?: 0.0) >= 40 -> ArrivalWeatherMood.WIND
    apparentTemperatureC <= 5 -> ArrivalWeatherMood.COLD
    apparentTemperatureC >= 28 -> ArrivalWeatherMood.HOT
    isDay && weatherCode in 0..1 -> ArrivalWeatherMood.SUN
    else -> ArrivalWeatherMood.MILD
}

/** Composable, stable copy: 12 conditions × 6 messages, independent of optional place/time slots. */
object WeatherSentenceEngine {
    private val messages = mapOf(
        ArrivalWeatherMood.THUNDER to listOf(
            "Gewitter sind möglich – plane den letzten Fußweg lieber mit etwas Schutz",
            "es könnte donnern; ein trockener Unterschlupf ist ein guter Plan",
            "der Himmel könnte Schlagzeug spielen – behalte das Wetter im Blick",
            "Gewitter könnten mitfahren; such dir am Ziel lieber einen geschützten Platz",
            "es könnte blitzen und donnern – der Spaziergang kann warten",
            "Gewitter sind angekündigt; ein Dach über dem Kopf wäre praktisch",
        ),
        ArrivalWeatherMood.FREEZING_RAIN to listOf(
            "gefrierender Niederschlag ist möglich – geh den letzten Weg vorsichtig",
            "es könnte glatt werden; kleine Schritte sind heute die clevere Abkürzung",
            "der Regen könnte gefrieren – feste Schuhe sind jetzt gute Reisebegleiter",
            "Vorsicht, mögliche Glätte! Plane für den Fußweg etwas mehr Zeit ein",
            "gefrierender Regen könnte warten – der Bahnsteig ist keine Schlittschuhbahn",
            "es könnte eisig werden; am Ziel lieber einen Gang herunterschalten",
        ),
        ArrivalWeatherMood.SNOW to listOf(
            "Schnee ist möglich – warme Schuhe dürfen mit",
            "es könnte schneien; die Mütze hat sich ein Ticket verdient",
            "mögliche Schneeflocken begleiten deinen letzten Fußweg",
            "der Himmel könnte Konfetti aus Schnee streuen – pack dich warm ein",
            "Schnee steht im Wetterfahrplan; Handschuhe wären keine schlechte Idee",
            "es könnte weiß werden – warme Füße machen die Ankunft gemütlicher",
        ),
        ArrivalWeatherMood.HEAVY_RAIN to listOf(
            "kräftiger Regen ist angesagt – dein Regenschirm möchte mit",
            "es könnte ordentlich schütten; eine Regenjacke ist eine gute Begleitung",
            "der Himmel plant womöglich eine Dusche – besser mit Regenschutz ankommen",
            "kräftige Schauer könnten warten; nimm lieber ein Stück Dach zum Mitnehmen mit",
            "viel Regen ist möglich – heute bekommt der Schirm einen Fensterplatz",
            "es könnte sehr nass werden; halte deine Regenjacke griffbereit",
        ),
        ArrivalWeatherMood.RAIN to listOf(
            "Regen ist möglich – denk an deinen Regenschirm",
            "es könnte regnen; deine Jacke darf wasserfest sein",
            "ein paar Tropfen könnten dich begrüßen – pack lieber den Schirm ein",
            "Regen steht auf dem Plan; trockene Socken sind die bessere Anschlussverbindung",
            "es könnte nass werden – ein kleiner Schirm reist unauffällig mit",
            "Regentropfen könnten warten; mit Kapuze kommt es sich entspannter an",
        ),
        ArrivalWeatherMood.POSSIBLE_RAIN to listOf(
            "Regen ist recht wahrscheinlich – ein kleiner Schirm wäre praktisch",
            "der Regen könnte Anschluss haben; nimm vorsichtshalber eine Kapuze mit",
            "es könnte regnen – der Schirm passt bestimmt noch ins Gepäck",
            "die Regenchance ist erhöht; lieber trocken vorbereitet ankommen",
            "der Himmel ist sich beim Regen noch unsicher – dein Schirm hilft bei beiden Antworten",
            "Regen liegt im Bereich des Möglichen; etwas Regenschutz schadet nicht",
        ),
        ArrivalWeatherMood.FOG to listOf(
            "Nebel ist möglich – die Aussicht könnte heute Verstecken spielen",
            "es könnte neblig sein; am Ziel lieber auf die Wegweiser schauen",
            "der Nebel könnte die Kulisse weichzeichnen – nimm dir Zeit zur Orientierung",
            "möglicherweise wartet Nebel; die Haltestelle bleibt trotzdem dein Ziel",
            "die Sicht könnte kürzer ausfallen als die Fahrt – Nebel ist angesagt",
            "Nebel könnte mit von der Partie sein; ein wacher Blick hilft beim Weitergehen",
        ),
        ArrivalWeatherMood.WIND to listOf(
            "es könnte kräftig pusten – halte deine Mütze fest",
            "Wind ist angesagt; deine Frisur reist womöglich auf eigene Faust",
            "eine windfeste Jacke könnte am Ziel Gold wert sein",
            "der Wind könnte dich begrüßen – lose Hüte lieber gut sichern",
            "es könnte zugig werden, auch nach dem Zug – nimm eine Jacke mit",
            "kräftiger Wind ist möglich; die Kapuze bekommt heute ihren Auftritt",
        ),
        ArrivalWeatherMood.COLD to listOf(
            "es fühlt sich kalt an – zieh dich lieber warm an",
            "die Jacke darf etwas dicker sein; am Ziel könnte es frisch werden",
            "es könnte ziemlich frisch sein – dein Schal möchte mit",
            "kalte Luft wartet wahrscheinlich am Ziel; warme Schichten lohnen sich",
            "die Temperaturen könnten nach Handschuhen verlangen",
            "es fühlt sich kühl an – warm eingepackt reist es sich gemütlicher",
        ),
        ArrivalWeatherMood.HOT to listOf(
            "es könnte heiß werden – eine Wasserflasche ist eine gute Mitfahrerin",
            "Wärme ist angesagt; such dir am Ziel gern ein schattiges Plätzchen",
            "es fühlt sich heiß an – denk unterwegs ans Trinken",
            "am Ziel könnte es sommerlich heiß sein; leichtes Gepäck und Wasser helfen",
            "die Wärme könnte mit ankommen – gönn dir eine Pause im Schatten",
            "es könnte ordentlich warm werden; deine Trinkflasche hat Anschluss",
        ),
        ArrivalWeatherMood.SUN to listOf(
            "die Sonne könnte dich begrüßen – die Sonnenbrille darf mit",
            "es sieht nach Sonne aus; vielleicht bleibt Zeit für einen kleinen Spaziergang",
            "freundliches Wetter ist in Sicht – ein Fensterplatz lohnt sich vielleicht",
            "Sonne steht im Wetterfahrplan; vergiss bei längerem Aufenthalt den Sonnenschutz nicht",
            "es könnte sonnig werden – deine Sonnenbrille wartet auf ihren Einsatz",
            "der Himmel zeigt sich voraussichtlich freundlich; gute Aussichten fürs Ankommen",
        ),
        ArrivalWeatherMood.MILD to listOf(
            "das Wetter wirkt eher entspannt – gute Aussichten für den letzten Fußweg",
            "es sieht nach ruhigem Wetter aus; am Ziel lässt es sich entspannt weitergehen",
            "der Himmel plant offenbar keinen großen Auftritt",
            "das Wetter dürfte sich im Hintergrund halten – genau wie eine gute Reisebegleitung",
            "es sieht recht unaufgeregt aus; manchmal ist das die beste Wetter-Nachricht",
            "das Wetter könnte gelassen bleiben – dein letzter Fußweg auch",
        ),
    )

    fun sentence(
        favorite: Favorite,
        settings: OverviewSettings,
        weather: ArrivalWeather,
        variant: Int,
        city: String? = favorite.location.city,
        hasRail: Boolean = false,
    ): List<OverviewTextPart> = buildList {
        // Arrival time remains owned exclusively by OverviewSentenceGenerator.
        val seed = variant.toLong() + favorite.id.hashCode() + weather.hour / 3_600_000
        val mood = weather.mood()
        val options = messages.getValue(mood)
        val message = tr(options[Math.floorMod(seed, options.size.toLong()).toInt()])
            .replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
        val municipality = city?.trim()?.takeIf {
            settings.showDestination && it.isNotEmpty() && it.length <= 80 && it.none(Char::isISOControl)
        }
        val playful = hasRail && DbHumorEngine.shouldDecorate(seed, mood)
        val warm = weather.apparentTemperatureC in 18.0..26.0 &&
            mood in setOf(ArrivalWeatherMood.SUN, ArrivalWeatherMood.MILD)
        if (playful && warm && municipality != null) {
            add(OverviewTextPart(tr("Wenn die Deutsche Bahn pünktlich ist, erwartet dich in ")))
            add(OverviewTextPart(municipality, OverviewEmphasis.PLACE))
            add(OverviewTextPart(tr(" voraussichtlich angenehm warmes Wetter.")))
        } else {
            if (municipality != null && Math.floorMod(seed, 3L) == 0L) {
                add(OverviewTextPart(tr("Bei deiner Ankunft in ")))
                add(OverviewTextPart(municipality, OverviewEmphasis.PLACE))
                add(OverviewTextPart(": "))
            } else {
                val leads = listOf(tr("Wenn du ankommst: "), tr("Sobald du das Ziel erreichst: "),
                    tr("Noch ein Wetterblick für deine Ankunft: "), tr("Für den letzten Weg: "))
                add(OverviewTextPart(leads[Math.floorMod(seed, leads.size.toLong()).toInt()]))
            }
            add(OverviewTextPart("$message."))
            if (playful) add(OverviewTextPart(" " + DbHumorEngine.weatherAside(seed)))
        }
    }
}

/** Editorial flavour, never a live operational status. No jokes obscure hazardous forecasts. */
object DbHumorEngine {
    fun shouldDecorate(seed: Long, mood: ArrivalWeatherMood): Boolean =
        Math.floorMod(seed, 5L) == 0L && mood !in setOf(ArrivalWeatherMood.THUNDER,
            ArrivalWeatherMood.FREEZING_RAIN, ArrivalWeatherMood.HEAVY_RAIN, ArrivalWeatherMood.SNOW)

    fun weatherAside(seed: Long): String {
        val asides = listOf(
            tr("Der Himmel hat immerhin keinen Bahnsteigwechsel angekündigt."),
            tr("Immerhin braucht das Wetter keine neue Sitzplatzreservierung."),
            tr("Die Wolken fahren heute ohne Wagenreihungsplan."),
            tr("Für Sonnenstrahlen ist zum Glück kein Schienenersatzverkehr vorgesehen."),
        )
        return asides[Math.floorMod(seed / 5, asides.size.toLong()).toInt()]
    }
}
