// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import dev.ambivalenton.openv.data.model.appFonts

/** Only visible popup rows and the selected family are loaded; font files stay unmodified. */
object AppTypefaces {
    private val cache=object:LruCache<String,Typeface>(12){}
    @Synchronized fun get(context:Context,id:String?):Typeface? {
        val option=appFonts.firstOrNull {it.id==id}?:return null
        return cache.get(option.id)?:Typeface.Builder(context.applicationContext.assets,option.asset).apply {
            setWeight(400);option.variations?.let {setFontVariationSettings(it)}
        }.build().also {cache.put(option.id,it)}
    }
}
@Composable fun appFontFamily(id:String?):FontFamily? {
    val context=LocalContext.current
    return remember(context,id){AppTypefaces.get(context,id)?.let {FontFamily(it)}}
}

@Composable fun appTypeface():Typeface?=AppTypefaces.get(LocalContext.current,LocalAppSettings.current.fontId)
