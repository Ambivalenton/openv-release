// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import dev.ambivalenton.openv.R
import kotlin.math.ceil

/**
 * Host-side translation on supported RemoteViews. No polling, focus request or private APIs.
 * TextView.setSelected is NOT a remotable method. API 31+ lets each animated child have the exact
 * full text width. Older hosts and disabled system animations get a readable, accessible static
 * row.
 */
@Composable
internal fun BoardTicker(
    text: String,
    color: Color,
    modifier: GlanceModifier,
    sizeSp: Float = 12f,
    viewportWidth: Float = LocalSize.current.width.value,
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    val fullWidth = ceil(paint.measureText(text) + metrics.density * 4)
    val viewport = viewportWidth.coerceAtLeast(1f) * metrics.density
    val animate =
        Build.VERSION.SDK_INT >= 31 &&
            fullWidth > viewport &&
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
    val layout =
        if (!animate) R.layout.board_ticker
        else
            when {
                (fullWidth + viewport) / metrics.density < 360 -> R.layout.board_ticker_12
                (fullWidth + viewport) / metrics.density < 600 -> R.layout.board_ticker_20
                else -> R.layout.board_ticker_32
            }
    val views =
        RemoteViews(context.packageName, layout).apply {
            for (id in
                if (animate) listOf(R.id.board_ticker_a, R.id.board_ticker_b)
                else listOf(R.id.board_ticker)) {
                setTextViewText(id, text)
                setTextColor(id, color.toArgb())
                setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizeSp)
                setContentDescription(id, text)
                if (animate && Build.VERSION.SDK_INT >= 31)
                    setViewLayoutWidth(id, fullWidth, TypedValue.COMPLEX_UNIT_PX)
            }
        }
    AndroidRemoteViews(views, modifier)
}
