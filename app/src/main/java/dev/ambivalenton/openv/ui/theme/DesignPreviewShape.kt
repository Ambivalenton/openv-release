// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One source of shape truth for selectors, sample canvases, palette previews and text badges. */
@Composable fun designPreviewShape(size:Dp=20.dp)=appCorner(size)
@Composable fun Modifier.clipDesignPreview(size:Dp=20.dp)=clip(designPreviewShape(size))
