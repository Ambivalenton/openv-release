// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr

/** Component names are persisted by Android. Never rename or remove an existing alias. */
@kotlinx.serialization.Serializable
enum class AppLogo(val titleKey:String,val descriptionKey:String,val preview:Int,val launcher:Int) {
    ORIGINAL("Original","Das vertraute Öpen-V-Zeichen",R.drawable.logo_original_preview,R.mipmap.ic_launcher),
    MODERN("Metro modern","Klare ÖV-Konturen auf kräftigem Petrol",R.drawable.logo_modern_preview,R.mipmap.logo_modern),
    MINIMAL("Minimal","Schwarzweiß und ein klares Zeichen",R.drawable.logo_minimal_preview,R.mipmap.logo_minimal),
    PIXEL("Pixelbahn","Eine kleine Bahn aus klaren Pixeln",R.drawable.logo_pixel_preview,R.mipmap.logo_pixel),
    RETRO("Retro-Emaille","Warme Emaillefarben wie an alten Haltestellen",R.drawable.logo_retro_preview,R.mipmap.logo_retro),
    DECO("Art déco","Goldene Geometrie auf tiefem Nachtblau",R.drawable.logo_deco_preview,R.mipmap.logo_deco),
    GLASS("Glaslicht","Lichtkanten und überlagerte Glasflächen",R.drawable.logo_glass_preview,R.mipmap.logo_glass),
    MAP("Linienplan","Bunte Linien und Haltepunkte als offenes V",R.drawable.logo_map_preview,R.mipmap.logo_map),
    ORIGAMI("Origami","Gefaltetes Papier, das zu deinem Weg wird",R.drawable.logo_origami_preview,R.mipmap.logo_origami),
    SUNRISE("Morgenrot","Ein heller Aufbruch zwischen Sonne und Schienen",R.drawable.logo_sunrise_preview,R.mipmap.logo_sunrise),
    ORBIT("Orbit","Eine kleine Bahn im großen Sternenraum",R.drawable.logo_orbit_preview,R.mipmap.logo_orbit),
    PLAY("Spielwürfel","Fröhliche Formen und ein kleines Fahrziel",R.drawable.logo_play_preview,R.mipmap.logo_play),
    NIGHT("Nachtlinie","Leuchtende Bahn vor einem ruhigen Nachthimmel",R.drawable.logo_night_preview,R.mipmap.logo_night),
    BRAND_PIXEL("Rasterzeichen","Das originale Zeichen in klaren Pixeln.",R.drawable.logo_brand_pixel_preview,R.mipmap.logo_brand_pixel),
    BRAND_NET("Netzzeichen","Das Öpen-V-Zeichen als feiner Netzplan.",R.drawable.logo_brand_net_preview,R.mipmap.logo_brand_net),
    BRAND_SKETCH("Stiftspur","Die vertraute Form, von Hand nachgezogen.",R.drawable.logo_brand_sketch_preview,R.mipmap.logo_brand_sketch),
    BRAND_MATRIX("Leuchtpunkte","Das Original auf einer dunklen Punktmatrix.",R.drawable.logo_brand_matrix_preview,R.mipmap.logo_brand_matrix),
    BRAND_DOTS("Punkt & Akzent","Schwarze Punkte mit wenigen roten Akzenten.",R.drawable.logo_brand_dots_preview,R.mipmap.logo_brand_dots),
    BRAND_SPECTRUM("Vierklang","Das vertraute Zeichen in vier klaren Farben.",R.drawable.logo_brand_spectrum_preview,R.mipmap.logo_brand_spectrum),
    // Ten new marks, several drawn from the bar chart, the network plan and the timeline.
    BARS("Balkenrhythmus","Vier Balken wie im Verbindungsdiagramm, auf Nachtblau",R.drawable.logo_bars_preview,R.mipmap.logo_bars),
    BAR_V("Balken-V","Die Balken deiner Verbindungen bilden das V",R.drawable.logo_bar_v_preview,R.mipmap.logo_bar_v),
    NETWORK("Netzknoten","Zwei Linien treffen sich zum V wie im Favoriten-Netzplan",R.drawable.logo_network_preview,R.mipmap.logo_network),
    NET_NIGHT("Nachtnetz","Leuchtende Netzplanlinien auf dunklem Grund",R.drawable.logo_net_night_preview,R.mipmap.logo_net_night),
    TIMELINE("Zeitstrahl","Haltepunkte auf einer Linie wie im Reiseverlauf",R.drawable.logo_timeline_preview,R.mipmap.logo_timeline),
    CLOCK("Bahnhofsuhr","Zeiger auf zehn nach zehn bilden das V",R.drawable.logo_clock_preview,R.mipmap.logo_clock),
    TICKET("Fahrschein","Ein Fahrschein mit gestanztem V auf Sonnengelb",R.drawable.logo_ticket_preview,R.mipmap.logo_ticket),
    RAILS("Gleisbogen","Zwei Gleise laufen im V zusammen",R.drawable.logo_rails_preview,R.mipmap.logo_rails),
    PIN("Ankerpunkt","Eine Ortsmarke mit dem V im Herzen",R.drawable.logo_pin_preview,R.mipmap.logo_pin),
    GRADIENT("Abendlicht","Ein klares V mit Umlautpunkten im Abendverlauf",R.drawable.logo_gradient_preview,R.mipmap.logo_gradient);
    val title get()=tr(titleKey)
    val description get()=tr(descriptionKey)
    val alias get()="dev.ambivalenton.openv.launcher.Logo"+name
}

/** Retired aliases stay addressable during updates, but are no longer offered as designs. */
val availableAppLogos=listOf(AppLogo.ORIGINAL,AppLogo.MODERN,AppLogo.MINIMAL,AppLogo.GLASS,
    AppLogo.BRAND_PIXEL,AppLogo.BRAND_NET,AppLogo.BRAND_SKETCH,AppLogo.BRAND_MATRIX,AppLogo.BRAND_DOTS,AppLogo.BRAND_SPECTRUM,
    AppLogo.PIXEL,AppLogo.RETRO,AppLogo.DECO,AppLogo.SUNRISE,AppLogo.NIGHT,
    AppLogo.BARS,AppLogo.BAR_V,AppLogo.NETWORK,AppLogo.NET_NIGHT,AppLogo.TIMELINE,AppLogo.CLOCK,AppLogo.TICKET,AppLogo.RAILS,AppLogo.PIN,AppLogo.GRADIENT)
fun AppLogo.currentReplacement()=when(this){AppLogo.MAP->AppLogo.BRAND_NET;AppLogo.PLAY->AppLogo.BRAND_SPECTRUM;AppLogo.ORBIT,AppLogo.ORIGAMI->AppLogo.ORIGINAL;else->this}
