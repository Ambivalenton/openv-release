// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measured gap between the lower edge of the screen title (with a source line, if
 * shown) and the rule closing the top bar. Screens that start right below the rule can repeat it, so
 * title, rule and content stand at equal distances.
 */
val LocalScreenTitleGap = compositionLocalOf { 16.dp }

/** Distance (dp) the lowest arrival in the comparison chart keeps above the navigation pill. */
internal val CHART_ABOVE_PILL = 10.dp
