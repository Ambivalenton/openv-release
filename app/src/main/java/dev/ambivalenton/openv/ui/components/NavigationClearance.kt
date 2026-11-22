// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
/** Space at the end of scrolling content; the viewport and its background still fill the screen. */
val LocalNavigationClearance=staticCompositionLocalOf {0.dp}
