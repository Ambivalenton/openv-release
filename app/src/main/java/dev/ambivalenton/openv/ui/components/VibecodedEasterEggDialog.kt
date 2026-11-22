// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.AppAlertDialog as AlertDialog

import dev.ambivalenton.openv.localization.tr

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

@Composable
fun VibecodedEasterEggDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FittedText(tr("Pssst... 🤫")) },
        text = {
            Text(tr("Die Idee fuhr schon lange mit. Der Code kam mit KI-Unterstützung dazu. Öpen-V ist vibecoded – und lebt von echten Wegen, neugierigen Menschen und deinem Feedback."))
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Geheimnis bewahren 🤫")) } },
    )
}
