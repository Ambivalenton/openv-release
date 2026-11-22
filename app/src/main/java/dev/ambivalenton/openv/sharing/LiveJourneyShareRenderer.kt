// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.sharing

import android.graphics.*
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.readableForeground

/** The live card of a pinned journey, drawn for the in-app compact view only. */
object LiveJourneyShareRenderer {
    /** Logical card width; the compact view scales it to the screen. */
    const val WIDTH=860
    private const val MAX_PIXELS=9_000_000f

    fun render(data: LiveJourneyShare,colors: ShareCardColors,scale: Float=1f): Bitmap {
        val text=SpannableStringBuilder()
        data.parts.forEach {part ->
            val start=text.length;text.append(part.text)
            if(part.emphasis!=LiveShareEmphasis.NORMAL) {
                val raw=when(part.emphasis) {LiveShareEmphasis.POSITION->0xFF1565C0L;LiveShareEmphasis.DELAY->0xFFB3261EL;else->0xFF00796BL}
                val ink=readableForeground(raw,colors.surface.toLong()).toInt()
                text.setSpan(ForegroundColorSpan(ink),start,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(StyleSpan(Typeface.BOLD),start,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=colors.text;textSize=29f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
        val layout=StaticLayout.Builder.obtain(text,0,text.length,paint,772).setIncludePad(false).setLineSpacing(9f,1f).build()
        val height=layout.height+204
        val factor=scale.coerceIn(.5f,minOf(4f,kotlin.math.sqrt(MAX_PIXELS/(WIDTH.toFloat()*height))))
        return Bitmap.createBitmap(kotlin.math.round(WIDTH*factor).toInt().coerceAtLeast(1),
            kotlin.math.round(height*factor).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {image ->
            val canvas=Canvas(image);canvas.drawColor(colors.background)
            canvas.scale(image.width/WIDTH.toFloat(),image.height/height.toFloat())
            val background=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=colors.surface}
            canvas.drawRoundRect(20f,20f,840f,height-20f,30f,30f,background)
            if(colors.cardNeedsOutline) canvas.drawRoundRect(20.75f,20.75f,839.25f,height-20.75f,30f,30f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=1.5f;color=colors.text;alpha=90})
            val title=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=colors.accent;textSize=29f;typeface=Typeface.DEFAULT_BOLD}
            canvas.drawText(tr("Öpen-V · Live-Verbindung"),44f,68f,title)
            title.color=colors.text;title.textSize=35f
            canvas.drawText(android.text.TextUtils.ellipsize(data.title,TextPaint(title),772f,android.text.TextUtils.TruncateAt.END).toString(),44f,119f,title)
            canvas.save();canvas.translate(44f,152f);layout.draw(canvas);canvas.restore()
        }
    }
}
