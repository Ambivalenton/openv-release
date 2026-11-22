// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*

internal val OVERVIEW_BODY_SIZE = 19.sp
internal val OVERVIEW_LINE_HEIGHT = 31.sp
internal val OVERVIEW_HEADER_SIZE = 16.sp

internal data class OverviewHighlightColors(val original: Color, val tonal: Color)

/** Times preserve the exact selected ARGB; other highlights change only its HCT tone. */
internal fun overviewHighlightColors(original: Color, dark: Boolean): OverviewHighlightColors {
    val source = Hct.fromInt(original.toArgb())
    val tone = (source.tone + if (dark) 18.0 else -18.0).coerceIn(0.0, 100.0)
    val tonal =
        Color(Hct.from(source.hue, source.chroma, tone).toInt()).copy(alpha = original.alpha)
    return OverviewHighlightColors(original, tonal)
}

@Composable
internal fun overviewFavoriteHighlights(seed: Long?): OverviewHighlightColors {
    val scheme = MaterialTheme.colorScheme
    val original = seed?.let { Color(it) } ?: scheme.primary
    return overviewHighlightColors(original, scheme.background.luminance() < .3f)
}

internal fun overviewAnnotatedString(
    parts: List<OverviewTextPart>,
    accent: Color,
    baseSize: TextUnit = OVERVIEW_BODY_SIZE,
    mutedAccent: Color = accent,
    originAccent:Color=accent,
    originMutedAccent:Color=mutedAccent,
): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        withStyle(
            SpanStyle(
                color =
                    when (part.emphasis) {
                        OverviewEmphasis.NORMAL -> Color.Unspecified
                        OverviewEmphasis.TIME, OverviewEmphasis.PLACE -> if(part.role in setOf(OverviewTextRole.DEPARTURE_TIME,OverviewTextRole.ORIGIN_PLACE,OverviewTextRole.BOARDING_PLATFORM))originAccent else accent
                        else -> if(part.role in setOf(OverviewTextRole.DEPARTURE_TIME,OverviewTextRole.ORIGIN_PLACE,OverviewTextRole.BOARDING_PLATFORM))originMutedAccent else mutedAccent
                    },
                fontSize =
                    if (part.emphasis in setOf(OverviewEmphasis.TIME, OverviewEmphasis.PLACE))
                        baseSize * 1.2f
                    else baseSize,
                fontWeight = if (part.emphasis == OverviewEmphasis.TIME) FontWeight.Bold else null,
                fontStyle = if (part.emphasis == OverviewEmphasis.PLACE) FontStyle.Italic else null,
            )
        ) {
            append(part.text)
        }
    }
}
