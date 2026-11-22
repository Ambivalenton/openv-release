// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.sharing

import dev.ambivalenton.openv.localization.tr

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.iconResource
import dev.ambivalenton.openv.ui.theme.readableForeground
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class JourneyShareData(
    val legs: List<TripSegment>,
    val totalDurationMinutes: Long,
    val date: String,
) {
    companion object {
        fun from(trip: Trip): JourneyShareData {
            val legs = trip.segments.filterNot { it.isWalk }
            require(legs.isNotEmpty()) { tr("Diese Verbindung enthält ausschließlich Fußwege.") }
            require(legs.size <= 24) {
                tr("Diese Verbindung enthält zu viele Etappen für eine kompakte Grafik.")
            }
            return JourneyShareData(
                legs,
                (trip.totalDuration + 59_999) / 60_000,
                Instant.ofEpochMilli(trip.startTime)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            )
        }
    }
}

data class ShareCardColors(
    val background: Int,
    val surface: Int,
    val text: Int,
    val accent: Int,
    val onAccent: Int,
) {
    /** True when card and ground are (almost) the same colour, e.g. in "Linie & Kontur". */
    val cardNeedsOutline: Boolean get() =
        androidx.core.graphics.ColorUtils.calculateContrast(surface or (0xFF shl 24), background or (0xFF shl 24)) < 1.12
    companion object {
        fun from(scheme: ColorScheme): ShareCardColors {
            val bg = scheme.surfaceContainerLow.toArgb()
            val accent = readableForeground(scheme.primary.toArgb().toLong(), bg.toLong()).toInt()
            return ShareCardColors(
                scheme.background.toArgb(),
                bg,
                readableForeground(scheme.onSurface.toArgb().toLong(), bg.toLong()).toInt(),
                accent,
                readableForeground(scheme.onPrimary.toArgb().toLong(), accent.toLong()).toInt(),
            )
        }
    }
}

/**
 * Draws a journey card for the compact view using off-screen Canvas rendering.
 * The scale sets the number of screen pixels per card unit.
 */
object JourneyShareRenderer {
    /** Logical card width; the compact view scales it to the screen. */
    const val WIDTH = 520
    /** Upper bound for very long journeys (24 legs), about 36 MB at ARGB_8888. */
    private const val MAX_PIXELS = 9_000_000f

    fun render(context: Context, data: JourneyShareData, colors: ShareCardColors, scale: Float = 1f): Bitmap {
        val width = WIDTH
        val namePaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.text
                textSize = 24f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        fun stopLayout(name: String) =
            StaticLayout.Builder.obtain(name, 0, name.length, namePaint, width - 172)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
        data class LegLayout(
            val origin: StaticLayout,
            val destination: StaticLayout,
            val arrivalTop: Float,
            val height: Int,
        )
        val layouts =
            data.legs.map { leg ->
                val origin = stopLayout(leg.origin.name)
                val destination = stopLayout(leg.destination.name)
                val arrivalTop =
                    49f + origin.height + (if (leg.departurePlatform != null) 24 else 0) + 14
                LegLayout(
                    origin,
                    destination,
                    arrivalTop,
                    (arrivalTop +
                            destination.height +
                            (if (leg.arrivalPlatform != null) 24 else 0) +
                            18)
                        .toInt(),
                )
            }
        val height = 142 + layouts.sumOf { it.height } + 16
        val factor = scale.coerceIn(.5f, minOf(4f, kotlin.math.sqrt(MAX_PIXELS / (width.toFloat() * height))))
        val image = createBitmap(kotlin.math.round(width * factor).toInt().coerceAtLeast(1),
            kotlin.math.round(height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(colors.background)
        canvas.scale(image.width / width.toFloat(), image.height / height.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = colors.surface
        canvas.drawRoundRect(16f, 16f, width - 16f, height - 16f, 28f, 28f, paint)
        if (colors.cardNeedsOutline) {
            // Outline designs fill nothing: a hairline keeps the card readable as one object.
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = colors.text; paint.alpha = 90
            canvas.drawRoundRect(16.75f, 16.75f, width - 16.75f, height - 16.75f, 28f, 28f, paint)
            paint.style = Paint.Style.FILL; paint.alpha = 255
        }
        fun text(
            value: String,
            x: Float,
            y: Float,
            size: Float,
            color: Int = colors.text,
            bold: Boolean = false,
            max: Float = width - x - 28,
        ) {
            val p =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    textSize = size
                    typeface =
                        Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                }
            canvas.drawText(
                TextUtils.ellipsize(value, p, max.coerceAtLeast(1f), TextUtils.TruncateAt.END)
                    .toString(),
                x,
                y,
                p,
            )
        }
        text(tr("Öpen-V"), 28f, 58f, 23f, colors.accent, true)
        text(
            tr("%1\$s Min.", data.totalDurationMinutes),
            (width - 176).toFloat(),
            60f,
            27f,
            bold = true,
            max = 150f,
        )
        text(data.date, 28f, 88f, 19f)
        text(
            tr("%1\$s – %2\$s", clockTime(data.legs.first().startTime), clockTime(data.legs.last().endTime)),
            28f,
            121f,
            27f,
            bold = true,
        )
        var y = 148f
        data.legs.forEachIndexed { index, leg ->
            val layout = layouts[index]
            paint.color = colors.accent
            canvas.drawRoundRect(28f, y, 138f, y + 37, 10f, 10f, paint)
            val icon =
                ResourcesCompat.getDrawable(context.resources, leg.lineType.iconResource(), null)
                    ?.mutate()
            icon?.setTint(colors.onAccent)
            icon?.setBounds(36, y.toInt() + 7, 59, y.toInt() + 30)
            icon?.draw(canvas)
            val measure =
                Paint()
                    .apply {
                        textSize = 17f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    .measureText(leg.lineName)
            val lineSize = (17f * 66 / measure.coerceAtLeast(1f)).coerceIn(11f, 17f)
            text(leg.lineName, 66f, y + 25, lineSize, colors.onAccent, true, 66f)
            text(leg.lineType.label, 160f, y + 25, 19f, colors.accent, max = 210f)
            if (leg.delay.cancelled)
                text(tr("Fällt aus"), (width - 150).toFloat(), y + 25, 19f, colors.accent, true)
            text(clockTime(leg.startTime), 28f, y + 72, 25f, bold = true, max = 110f)
            canvas.withTranslation(144f, y + 49) { layout.origin.draw(this) }
            platformName(leg.departurePlatform,leg.lineType)?.let {
                text(it, 144f, y + 49 + layout.origin.height + 18, 17f)
            }
            text(
                clockTime(leg.endTime),
                28f,
                y + layout.arrivalTop + 23,
                25f,
                bold = true,
                max = 110f,
            )
            canvas.withTranslation(144f, y + layout.arrivalTop) { layout.destination.draw(this) }
            platformName(leg.arrivalPlatform,leg.lineType)?.let {
                text(
                    it,
                    144f,
                    y + layout.arrivalTop + layout.destination.height + 18,
                    17f,
                )
            }
            if (index < data.legs.lastIndex) {
                paint.color = colors.accent
                paint.alpha = 55
                paint.strokeWidth = 1f
                canvas.drawLine(
                    28f,
                    y + layout.height - 7,
                    (width - 28).toFloat(),
                    y + layout.height - 7,
                    paint,
                )
                paint.alpha = 255
            }
            y += layout.height
        }
        return image
    }

    /** Spoken equivalent of the card for TalkBack (the card itself is a picture). */
    fun description(data: JourneyShareData): String =
        (listOf(tr("Öpen-V"), data.date, tr("%1\$s – %2\$s", clockTime(data.legs.first().startTime), clockTime(data.legs.last().endTime)),
            tr("%1\$s Min.", data.totalDurationMinutes)) +
            data.legs.map { leg ->
                tr("%1\$s %2\$s: %3\$s %4\$s bis %5\$s %6\$s", leg.lineType.label, leg.lineName, clockTime(leg.startTime),
                    leg.origin.name, clockTime(leg.endTime), leg.destination.name) +
                    (if (leg.delay.cancelled) ", " + tr("Fällt aus") else "")
            }).joinToString(". ")
}
