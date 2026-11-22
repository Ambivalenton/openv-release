// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun ContinuousSettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChange: (Float) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth()) {
        Text(
            tr("%1\$s · %2\$s %3\$s", label, String.format(locale, "%.1f", value), unit),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value.coerceIn(range),
            onChange,
            valueRange = range,
            steps = 0,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
