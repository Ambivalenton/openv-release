// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable

/**
 * Look of the favourite network plan (build 70). Öpen-V keeps the plan as it was; the other designs
 * follow well-known metro maps in their general style (line colours, line weight, corners, station
 * marks). They borrow no logos or names of the operators. Favourites keep their symbols everywhere.
 */
@Serializable
enum class NetworkPlanDesign(internal val titleKey: String, internal val descriptionKey: String) {
    // Drawn from the active app theme instead of a city's metro map.
    APP_THEME("App-Theme", "Farben, Formen, Transparenz und Motive deines App-Themes – pixelig, gemalt, leuchtend oder gezeichnet, je nach Design"),
    OPENV("Öpen-V", "Gegenfarben deiner Favoriten mit hellem Rand und Glanzstreifen auf ruhigem Kartenblatt"),
    LONDON("London", "Strenge 45°-Diagonalen, enge Kurven und weiße Umsteigeringe mit schwarzem Rand"),
    NEW_YORK("New York", "Kräftige Linienbänder mit geraden Enden und schwarze Stationspunkte"),
    TOKYO("Tokio", "Weiße Stationen mit farbigem Ring und die bunte Palette der Tokioter Metro"),
    PARIS("Paris", "Cremefarbenes Papier, feine sanft geschwungene Linien und die Farben der Métro"),
    BERLIN("Berlin", "U-Bahn-Farben mit weißem Rand und klare Umsteigeknoten"),
    MOSCOW("Moskau", "Weich fließende Kurven und kräftige Farben wie im Moskauer Metroplan"),
    HONG_KONG("Hongkong", "Breite Linien mit weißem Rand, runde Ecken und helle Stationen"),
    WASHINGTON("Washington", "Sehr breite Farbbänder und weiße Stationsscheiben mit dunklem Rand"),
    MEXICO_CITY("Mexiko-Stadt", "Jede Station ein Symbol auf einer Kachel in der Farbe ihrer Linie"),
    SEOUL("Seoul", "Feine Linien, viel Luft und weiße Stationen mit farbigem Ring"),
    SINGAPORE("Singapur", "Kräftige MRT-Farben, gerade Wege und weiße Stationen mit dunklem Rand"),
    // Unusual maps from around the world, each with its own line and station language.
    MADRID("Madrid", "Zwölf Linienfarben, knappe Ecken und runde weiße Stationen mit dunklem Ring"),
    CHICAGO("Chicago", "Breite Hochbahnbänder mit dunkler Kante, harte Ecken und eckige Stationen"),
    LISBON("Lissabon", "Hügelige, frei schwingende Linien auf Azulejo-Weiß und Stationen mit farbigem Ring"),
    SHANGHAI("Shanghai", "Dichtes Netz aus feinen Linien in vielen Farben, abgerundet-eckige Stationen"),
    DELHI("Delhi", "Pink, Magenta und Violett neben klassischen Farben, weiße Kanten und runde Stationen"),
    SAO_PAULO("São Paulo", "Sehr breite Linien mit weißem Rand und farbige Stationsscheiben"),
    BUENOS_AIRES("Buenos Aires", "Doppelstrang-Linien der Subte und weiße Stationen mit dunklem Ring"),
    STOCKHOLM("Stockholm", "Hohle Linien mit hellem Kern auf kühlem Papier und weiße Ringstationen"),
    TAIPEI("Taipeh", "Weich gerundete Linien in sechs MRT-Farben und Stationen mit farbigem Ring"),
    DUBAI("Dubai", "Leuchtröhren auf nachtblauem Blatt, auch im hellen Modus"),
    ISTANBUL("Istanbul", "Viele Farben zwischen zwei Kontinenten, Rauten als Stationen"),
    BUDAPEST("Budapest", "Gerade Enden, kräftige Grundfarben und schwarze Stationspunkte"),
    OSAKA("Osaka", "Bahnsignatur: farbige Linien mit hellen Schwellen wie auf japanischen Bahnkarten");

    val title: String get() = tr(titleKey)
    val description: String get() = tr(descriptionKey)
}
