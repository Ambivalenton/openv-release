// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import android.content.res.AssetManager
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.ambivalenton.openv.data.model.appFonts

/**
 * Typefaces of the designs, loaded from the bundled, unmodified OFL files. Variable fonts
 * receive real weights through their 'wght' axis; static files are declared once at 400, so Compose
 * may still synthesise bold for them. Nothing is downloaded; families are created lazily and cached.
 */
internal val variableThemeFonts=setOf("inter","roboto","opensans","sourcesans3","montserrat","raleway","manrope","dmsans",
    "outfit","spacegrotesk","nunito","quicksand","worksans","rubik","robotocondensed","oswald","overpass","teko","lora",
    "merriweather","bitter","playfairdisplay","ebgaramond","librebaskerville","cormorantgaramond","cinzel","bodonimoda",
    "jetbrainsmono","firacode","inconsolata","pixelifysans","caveat","dancingscript","fredoka","baloo2",
    "atkinsonhyperlegiblenext","lexend")

private object ThemeFontFamilies {
    private val cache=LruCache<String,FontFamily>(16)
    @Synchronized fun get(assets:AssetManager,id:String):FontFamily? {
        cache.get(id)?.let {return it}
        val option=appFonts.firstOrNull {it.id==id} ?: return null
        val weights=if(id in variableThemeFonts || option.variations?.contains("wght")==true) listOf(300,400,500,600,700,800,900) else listOf(400)
        val family=FontFamily(weights.map {w->Font(option.asset,assets,FontWeight(w))})
        cache.put(id,family)
        return family
    }
}

/** The family for a bundled font id, or null when the id is unknown. */
@Composable internal fun themeFontFamily(id:String?):FontFamily? {
    val context=LocalContext.current
    return remember(context,id) {id?.let {ThemeFontFamilies.get(context.applicationContext.assets,it)}}
}
