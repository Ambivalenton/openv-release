// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.FavoriteBadgeArtwork
import dev.ambivalenton.openv.ui.theme.*
import dev.ambivalenton.openv.widget.GlanceThemeHelper

@Composable
fun FavoriteBadge(favorite: Favorite, modifier: Modifier = Modifier, size: Dp = 48.dp,
    iconColor: Long? = null, shadow: Boolean = false, singleShape:Boolean=false) {
    val colors = favoriteColors(favorite.accentSeed,
        MaterialTheme.colorScheme.background.luminance() < .3f, MaterialTheme.colorScheme)
    val base = remember { FavoriteBadgeArtwork.mask(IconStyle.SQUIRCLE, false).asImageBitmap() }
    val accent = remember(favorite.shape) { FavoriteBadgeArtwork.mask(favorite.shape, true).asImageBitmap() }
    val tint = iconColor?.let { Color(readableForeground(it, colors.accent.toArgb().toLong())) }
        ?: colors.onAccent
    // Own shadow instead of the platform elevation shadow (crash with concave shapes, see shapeShadow).
    Box(modifier.size(size).then(if (shadow) Modifier.shapeShadow(
        GlanceThemeHelper.composeShape(if(singleShape)favorite.shape else IconStyle.SQUIRCLE),2.dp) else Modifier),
        contentAlignment = Alignment.Center) {
        if(!singleShape) Image(base, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(colors.container))
        if(singleShape) Box(Modifier.fillMaxSize().background(colors.accent,GlanceThemeHelper.composeShape(favorite.shape)))
        else Image(accent, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(colors.accent))
        AppIcon(favorite.icon.resource(), null, Modifier.size(size * FavoriteBadgeArtwork.iconFraction(favorite.shape)), tint = tint)
    }
}
