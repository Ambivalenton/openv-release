// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Display-only. Never change routing IDs, saved names, search queries or shared journeys. */
fun LineType.keepsRailStopCity()=this in setOf(LineType.ICE,LineType.REGIONAL,LineType.SUBURBAN)

// A conservative fallback for providers that omit their structured municipality. Unknown first
// words are deliberately retained: e.g. "Am Markt" and "Frankfurter Allee" are stop names.
private val stopMunicipalities=("Mülheim an der Ruhr|Mülheim a.d. Ruhr|Castrop-Rauxel|Bergisch Gladbach|Bad Oeynhausen|"+
    "Recklinghausen|Gelsenkirchen|Mönchengladbach|Lüdinghausen|Kamp-Lintfort|Schloß Holte-Stukenbrock|"+
    "Frankfurt am Main|Frankfurt (Main)|Frankfurt (Oder)|Ludwigshafen am Rhein|Neustadt an der Weinstraße|"+
    "Dortmund|Bochum|Herne|Essen|Duisburg|Oberhausen|Bottrop|Gladbeck|Herten|Datteln|Waltrop|Oer-Erkenschwick|"+
    "Witten|Wetter|Herdecke|Hagen|Schwerte|Lünen|Kamen|Bergkamen|Unna|Holzwickede|Fröndenberg|Selm|"+
    "Haltern am See|Dorsten|Marl|Dülmen|Coesfeld|Lüdenscheid|Iserlohn|Menden|Altena|Ennepetal|Gevelsberg|Schwelm|"+
    "Düsseldorf|Krefeld|Neuss|Meerbusch|Willich|Viersen|Kempen|Tönisvorst|Moers|Rheinberg|Kleve|Xanten|Wesel|"+
    "Dinslaken|Voerde|Hünxe|Emmerich am Rhein|Kalkar|Goch|Geldern|Nettetal|Ratingen|Heiligenhaus|Velbert|"+
    "Wülfrath|Wuppertal|Solingen|Remscheid|Hilden|Haan|Erkrath|Mettmann|Langenfeld|Monheim am Rhein|"+
    "Köln|Bonn|Leverkusen|Aachen|Münster|Bielefeld|Paderborn|Hamm|Siegen|Gütersloh|Detmold|Herford|"+
    "Berlin|Hamburg|München|Bremen|Hannover|Leipzig|Dresden|Stuttgart|Nürnberg|Karlsruhe|Mannheim|"+
    "Freiburg im Breisgau|Heidelberg|Mainz|Wiesbaden|Kassel|Kiel|Lübeck|Rostock|Magdeburg|Erfurt|Potsdam|"+
    "Saarbrücken|Trier|Koblenz|Augsburg|Regensburg|Würzburg|Ulm|Oldenburg|Osnabrück|Braunschweig|Chemnitz")
    .split('|').sortedByDescending {it.length}

/** Keep the municipality at rail interchange stations, including bus and tram platforms. */
fun Location.isRailStationName():Boolean =
    Regex("(?:^|[\\s,(/-])(?:hbf|bf|bhf|zob|[\\p{L}-]*bahnhof|bahnhofsplatz|central station|railway station|bus station)(?:$|[\\s,.()/\\-])",RegexOption.IGNORE_CASE).containsMatchIn(name)

@Suppress("UNUSED_PARAMETER")
fun displayStopName(location:Location,lineType:LineType,municipalities:Set<String> = emptySet()):String {
    val name=location.name.trim()
    if(location.isRailStationName())return name
    val candidates=(listOfNotNull(location.city?.trim()?.takeIf {it.isNotEmpty()})+municipalities+stopMunicipalities)
        .distinct().sortedByDescending {it.length}
    for(city in candidates) {
        if(!name.startsWith(city,ignoreCase=true) || name.length<=city.length)continue
        val boundary=name[city.length]
        if(boundary!=',' && !boundary.isWhitespace())continue
        val short=name.drop(city.length).trimStart(' ',',','\t')
        if(short.isNotEmpty())return short
    }
    return name
}

fun railStopNameScale(textWidth:Float,availableWidth:Float,isRail:Boolean):Float =
    if(!isRail || !textWidth.isFinite() || !availableWidth.isFinite() || textWidth<=0f)1f
    else (availableWidth.coerceAtLeast(0f)/textWidth).coerceIn(.7f,1f)
