// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.theme

import dev.ambivalenton.openv.localization.tr

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

data class FavoriteColorPreset(val name: String, val seed: Long)

/** Stable seeds, never array indices, are persisted so older favourites keep their colour. */
val favoriteColorPresets: List<FavoriteColorPreset>
    get() =
    listOf(
        FavoriteColorPreset(tr("Lagune"), 0xFF006A6AL),
        FavoriteColorPreset(tr("Azur"), 0xFF245EA8L),
        FavoriteColorPreset(tr("Iris"), 0xFF6555A7L),
        FavoriteColorPreset("Rosé", 0xFFA63D70L),
        FavoriteColorPreset(tr("Sonnengold"), 0xFF896100L),
        FavoriteColorPreset(tr("Moos"), 0xFF50662DL),
        FavoriteColorPreset(tr("Smaragd"), 0xFF087E62L),
        FavoriteColorPreset(tr("Rubin"), 0xFFAE344DL),
        FavoriteColorPreset(tr("Orchidee"), 0xFF8846A1L),
        FavoriteColorPreset(tr("Koralle"), 0xFFB64A43L),
        FavoriteColorPreset(tr("Türkis"), 0xFF007C91L),
        FavoriteColorPreset(tr("Mitternacht"), 0xFF404B92L),
        FavoriteColorPreset(tr("Himbeere"), 0xFFA14680L),
        FavoriteColorPreset(tr("Salbei"), 0xFF55715AL),
        FavoriteColorPreset(tr("Schiefer"), 0xFF526775L),
        FavoriteColorPreset(tr("Mandarine"), 0xFFAB591FL),
        FavoriteColorPreset(tr("Lavendel"), 0xFF79629BL),
        FavoriteColorPreset(tr("Pflaume"), 0xFF804A74L),
        FavoriteColorPreset(tr("Ozean"), 0xFF256477L),
        FavoriteColorPreset(tr("Terrakotta"), 0xFF94543DL),
    )

data class FavoriteColorTokens(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color,
)

/** Opaque tonal fills let the same readable pair work on every launcher wallpaper. */
fun contrastContentColor(container: Color): Color {
    val opaque = container.toArgb() or (0xFF shl 24)
    return if (
        ColorUtils.calculateContrast(android.graphics.Color.WHITE, opaque) >=
            ColorUtils.calculateContrast(android.graphics.Color.BLACK, opaque)
    )
        Color.White
    else Color.Black
}

/** Time rules only change emphasis. A regular card always uses its primary tonal container. */
fun favoriteColors(
    seed: Long?,
    dark: Boolean,
    fallback: ColorScheme,
    emphasized: Boolean = false,
): FavoriteColorTokens {
    val palette = seed?.let { seedColorScheme(it, dark) } ?: fallback
    val container = if (emphasized) palette.primary else palette.primaryContainer
    return FavoriteColorTokens(
        container,
        contrastContentColor(container),
        palette.primary,
        contrastContentColor(palette.primary),
    )
}
