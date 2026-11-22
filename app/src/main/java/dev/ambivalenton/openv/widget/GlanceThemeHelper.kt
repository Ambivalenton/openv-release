// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.glance.color.ColorProvider
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.favoriteColors
import dev.ambivalenton.openv.ui.theme.isDarkTheme
import dev.ambivalenton.openv.ui.theme.resolveColorScheme
import kotlin.math.*

/** One geometry implementation is used by both native bitmap masks and the settings preview. */
object GlanceThemeHelper {
    /** Square/tall slots also show a horizontal capsule, rather than silently becoming circles. */
    fun capsuleBounds(rect: RectF): RectF {
        val height = minOf(rect.height(), rect.width() / 2f).coerceAtLeast(0f)
        return RectF(rect.left, rect.centerY() - height / 2, rect.right, rect.centerY() + height / 2)
    }

    fun shapePath(rect: RectF, shape: IconStyle?, radius: Float = 20f): Path =
        Path().apply {
            when (shape) {
                IconStyle.CIRCLE -> addOval(rect, Path.Direction.CW)
                IconStyle.SQUIRCLE -> {
                    for (i in 0..120) {
                        val angle = 2 * PI * i / 120
                        val c = cos(angle)
                        val s = sin(angle)
                        val x = rect.centerX() + sign(c) * abs(c).pow(.5) * rect.width() / 2
                        val y = rect.centerY() + sign(s) * abs(s).pow(.5) * rect.height() / 2
                        if (i == 0) moveTo(x.toFloat(), y.toFloat())
                        else lineTo(x.toFloat(), y.toFloat())
                    }
                    close()
                }
                IconStyle.TEARDROP ->
                    addRoundRect(
                        rect,
                        floatArrayOf(
                            rect.width() * .5f,
                            rect.height() * .5f,
                            rect.width() * .5f,
                            rect.height() * .5f,
                            rect.width() * .08f,
                            rect.height() * .08f,
                            rect.width() * .5f,
                            rect.height() * .5f,
                        ),
                        Path.Direction.CW,
                    )
                IconStyle.PILL -> {
                    val capsule = capsuleBounds(rect)
                    val r = capsule.height() / 2f
                    addRoundRect(capsule, r, r, Path.Direction.CW)
                }
                IconStyle.STAR -> {
                    for (i in 0 until 10) {
                        val angle = -PI / 2 + PI * i / 5
                        val r = if (i % 2 == 0) 1f else .55f
                        val x = rect.centerX() + cos(angle).toFloat() * rect.width() / 2 * r
                        val y = rect.centerY() + sin(angle).toFloat() * rect.height() / 2 * r
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                IconStyle.HEXAGON,
                IconStyle.OCTAGON,
                IconStyle.DIAMOND -> {
                    val points = when (shape) { IconStyle.HEXAGON -> 6; IconStyle.OCTAGON -> 8; else -> 4 }
                    for (i in 0 until points) {
                        val angle = -PI / 2 + 2 * PI * i / points
                        val x = rect.centerX() + cos(angle).toFloat() * rect.width() / 2
                        val y = rect.centerY() + sin(angle).toFloat() * rect.height() / 2
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                IconStyle.COOKIE,
                IconStyle.CLOVER -> {
                    val lobes = if (shape == IconStyle.COOKIE) 10 else 4
                    for (i in 0..180) {
                        val angle = 2 * PI * i / 180 - PI / 2
                        val radiusFactor =
                            if (shape == IconStyle.COOKIE) .88 + .12 * cos(lobes * angle)
                            else .77 + .23 * cos(lobes * angle)
                        val x =
                            rect.centerX() +
                                cos(angle).toFloat() * rect.width() / 2 * radiusFactor.toFloat()
                        val y =
                            rect.centerY() +
                                sin(angle).toFloat() * rect.height() / 2 * radiusFactor.toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                else -> {
                    // One unit-square outline for app masks and widget drawables.
                    val outline = shape?.let { dev.ambivalenton.openv.rendering.ExpressiveShapeOutline.points(it) }
                    if (outline != null) {
                        outline.forEachIndexed { i, (px, py) ->
                            val x = rect.left + px.toFloat() * rect.width()
                            val y = rect.top + py.toFloat() * rect.height()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    } else addRoundRect(
                        rect,
                        radius.coerceAtMost(min(rect.width(), rect.height()) / 2),
                        radius.coerceAtMost(min(rect.width(), rect.height()) / 2),
                        Path.Direction.CW,
                    )
                }
            }
        }

    fun composeShape(style: IconStyle): Shape =
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ) =
                Outline.Generic(
                    shapePath(
                            RectF(0f, 0f, size.width, size.height),
                            style,
                            min(size.width, size.height) * .28f,
                        )
                        .asComposePath()
                )
        }

    fun favoritePair(
        context: Context,
        settings: AppSettings,
        seed: Long?,
        emphasized: Boolean = false,
    ): WidgetColors {
        val actualSeed = seed ?: settings.accentSeed
        val day =
            favoriteColors(
                actualSeed,
                dev.ambivalenton.openv.data.model.themeDark(settings, false),
                resolveColorScheme(context, settings, false),
                emphasized,
            )
        val night =
            favoriteColors(
                actualSeed,
                dev.ambivalenton.openv.data.model.themeDark(settings, true),
                resolveColorScheme(context, settings, true),
                emphasized,
            )
        val dayContainer = day.container
        val nightContainer = night.container
        fun icon(
            background: androidx.compose.ui.graphics.Color,
            fallback: androidx.compose.ui.graphics.Color,
        ) =
            settings.widget.iconColor?.let {
                androidx.compose.ui.graphics.Color(
                    dev.ambivalenton.openv.ui.theme.readableForeground(
                        it,
                        background.toArgb().toLong() and 0xffffffffL,
                    )
                )
            } ?: fallback
        return WidgetColors(
            ColorProvider(dayContainer, nightContainer),
            ColorProvider(
                dev.ambivalenton.openv.ui.theme.contrastContentColor(dayContainer),
                dev.ambivalenton.openv.ui.theme.contrastContentColor(nightContainer),
            ),
            ColorProvider(day.accent, night.accent),
            ColorProvider(icon(day.accent, day.onAccent), icon(night.accent, night.onAccent)),
            ColorProvider(
                icon(dayContainer, dev.ambivalenton.openv.ui.theme.contrastContentColor(dayContainer)),
                icon(nightContainer, dev.ambivalenton.openv.ui.theme.contrastContentColor(nightContainer)),
            ),
        )
    }
}

data class WidgetColors(
    val container: androidx.glance.unit.ColorProvider,
    val content: androidx.glance.unit.ColorProvider,
    val accent: androidx.glance.unit.ColorProvider,
    val onAccent: androidx.glance.unit.ColorProvider,
    val icon: androidx.glance.unit.ColorProvider = content,
)
