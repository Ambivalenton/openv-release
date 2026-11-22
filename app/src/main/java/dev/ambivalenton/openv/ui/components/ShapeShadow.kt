// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Number of translucent copies; six give a soft edge at any size used in the app. */
internal const val SHAPE_SHADOW_STEPS = 6
/** Opacity in the core of the shadow; every copy adds one sixth of it. */
internal const val SHAPE_SHADOW_ALPHA = .26f

/**
 * Drop shadow for the favourite shapes, drawn by the app itself (build 67).
 *
 * The platform elevation shadow (Modifier.shadow) hands the shape's outline to the system renderer.
 * That renderer is made for convex outlines: with the indented favourite shapes "Cookie" and
 * "Kleeblatt" the app crashed on real devices as soon as such a favourite was shown in the network
 * plan, and it kept crashing because the favourite was saved. The 120-corner "Squircle" made the
 * system shadow flicker while a node moved. Software rendering (unit tests, screenshots) never
 * takes that path, which is why it stayed unnoticed.
 *
 * This shadow is a few translucent copies of the same path, slightly below the shape and growing
 * outwards. It needs no outline, no blur and no bitmap, looks the same on every device and cannot
 * flicker. The path is built once per size.
 */
fun Modifier.shapeShadow(shape: Shape, elevation: Dp, ink: Color = Color.Black): Modifier =
    if (elevation <= 0.dp) this else drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val lift = elevation.toPx()
        val smallest = size.minDimension.coerceAtLeast(1f)
        onDrawBehind {
            for (step in SHAPE_SHADOW_STEPS downTo 1) {
                val spread = lift * 1.1f * step / SHAPE_SHADOW_STEPS
                val grow = 1f + 2f * spread / smallest
                withTransform({ translate(0f, lift * .45f); scale(grow, grow, center) }) {
                    drawPath(path, ink.copy(alpha = SHAPE_SHADOW_ALPHA / SHAPE_SHADOW_STEPS))
                }
            }
        }
    }
