// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.data.model.BoardDesign

/** Functional row action is shared even by custom theme painters. Never obscures their content. */
internal fun drawBoardJourneyLinks(canvas:Canvas,model:BoardPresentation,width:Float,layout:BoardLayout,fontScale:Float) {
    if(layout.journeyLinkHeight<=0 || model.isDesignPreview)return
    val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color=model.colors.accent.toInt();textSize=9f*fontScale.coerceIn(1f,2f)
        typeface=Typeface.create(model.typeface ?: model.themeTypeface ?: Typeface.SANS_SERIF,Typeface.NORMAL)
    }
    // Real display technologies write the link in their own characters; curated screens and the
    // "App-Theme" board (printed in the app's material) use plain type.
    val display=displayBoardTheme(model)?.takeIf {theme->when(model.design) {
        BoardDesign.APP_INTEGRATED->false
        BoardDesign.APP_THEME->displayTechnologyFor(theme)!=null
        else->true
    }}?.let {DisplayText(canvas,paint.typeface ?: Typeface.SANS_SERIF,displayBoardTechnology(model,it))}
    model.rows.take(layout.rows).forEachIndexed {index,row->
        if(row.journey!=null) {
            val text=TextUtils.ellipsize(tr("Zeitstrahl öffnen"),paint,width-2*layout.padding,TextUtils.TruncateAt.END).toString()
            val top=layout.padding+layout.header+index*layout.rowHeight+layout.rowHeight-layout.journeyLinkHeight
            val baseline=top+layout.journeyLinkHeight/2-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2
            if(display!=null)display.draw(text,layout.padding+4,top+layout.journeyLinkHeight/2,
                width-2*layout.padding-4,paint.textSize,layout.journeyLinkHeight,paint.color)
            else canvas.drawText(text,layout.padding+4,baseline,paint)
        }
    }
}
