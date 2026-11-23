// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.ui.theme.contrastContentColor
import dev.ambivalenton.openv.ui.theme.resolveColorScheme

/**
 * Build 107 · one look for both widgets: the app's own colours for day and night, flat rounded
 * surfaces (shape drawables tinted by the host, so the corners also work before Android 12, where
 * they follow the launcher's radius) and real text instead of drawn pictures.
 */
internal data class WidgetPalette(
    val surface: ColorProvider,
    val tile: ColorProvider,
    val onSurface: ColorProvider,
    val muted: ColorProvider,
    val accent: ColorProvider,
    val accentContainer: ColorProvider,
    val onAccentContainer: ColorProvider,
    val late: ColorProvider,
    val error: ColorProvider,
)

private fun pair(day: Color, night: Color): ColorProvider = androidx.glance.color.ColorProvider(day, night)

/** The app's scheme for day and night; a background chosen in the settings wins, with readable ink on it. */
internal fun widgetPalette(context: Context, settings: AppSettings): WidgetPalette {
    val day = resolveColorScheme(context, settings, false)
    val night = resolveColorScheme(context, settings, true)
    fun of(role: (ColorScheme) -> Color) = pair(role(day), role(night))
    val custom = settings.widget.backgroundColor?.let { Color(it).copy(alpha = 1f) }
    val surface = custom?.let { pair(it, it) } ?: of { it.surfaceContainer }
    val ink = custom?.let { contrastContentColor(it) }
    return WidgetPalette(
        surface = surface,
        tile = custom?.let { androidx.compose.ui.graphics.lerp(it, ink!!, .08f).let { t -> pair(t, t) } } ?: of { it.surfaceContainerHighest },
        onSurface = ink?.let { pair(it, it) } ?: of { it.onSurface },
        muted = ink?.let { pair(it.copy(alpha = .72f), it.copy(alpha = .72f)) } ?: of { it.onSurfaceVariant },
        accent = of { it.primary },
        accentContainer = of { it.primaryContainer },
        onAccentContainer = of { it.onPrimaryContainer },
        // Delays: amber, not alarm red; a cancellation uses the scheme's error colour.
        late = pair(Color(0xFFA45A00), Color(0xFFFFB866)),
        error = of { it.error },
    )
}

/** Shapes the host tints: the widget's surface, inner tiles, small badges and round buttons. */
internal enum class WidgetShape(val drawable: Int) {
    OUTER(R.drawable.widget_shape_outer), INNER(R.drawable.widget_shape_inner),
    SMALL(R.drawable.widget_shape_small), PILL(R.drawable.widget_shape_pill)
}

internal fun GlanceModifier.widgetFill(shape: WidgetShape, color: ColorProvider): GlanceModifier =
    background(ImageProvider(shape.drawable), ContentScale.FillBounds, ColorFilter.tint(color))

/** One line (or [lines]) of widget text; long names end with an ellipsis instead of being cut. */
@Composable internal fun WidgetText(
    text: String,
    color: ColorProvider,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    lines: Int = 1,
    struck: Boolean = false,
) = Text(text, modifier, style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align,
    textDecoration = if (struck) TextDecoration.LineThrough else null), maxLines = lines)

/** Width of a line badge that fits its name ("U35", "SB20", "ICE 1234"), in dp. */
internal fun lineBadgeWidth(name: String, font: Float): Float = (22f + name.length.coerceIn(1, 8) * 7.2f) * font.coerceIn(.85f, 1.4f)

internal val Float.dpv: Dp get() = Dp(this)
