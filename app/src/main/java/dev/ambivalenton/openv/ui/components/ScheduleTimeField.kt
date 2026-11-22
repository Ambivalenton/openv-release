// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import dev.ambivalenton.openv.ui.components.AppDialog as Dialog

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A button, not a disabled/editable text field: TalkBack and hardware keyboards can activate it. */
@Composable
fun ScheduleTimeField(label: String, minute: Int, onChange: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val time = LocalTime.of(minute.coerceIn(0, 1439) / 60, minute.coerceIn(0, 1439) % 60)
    val locale=LocalConfiguration.current.locales[0]
    val formatted = if (DateFormat.is24HourFormat(LocalContext.current))
        time.format(DateTimeFormatter.ofPattern("HH:mm"))
    else time.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    ThemedOutlinedButton(
        onClick = { focus.clearFocus(force = true); keyboard?.hide(); open = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        AppIcon(R.drawable.ic_clock, null)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        FittedText(formatted, style = MaterialTheme.typography.titleMedium)
    }
    if (open) MinuteTimePickerDialog(label, minute, { open = false }) {
        onChange(it)
        open = false
    }
}

/** Draft state survives recreation. Cancel and Back deliberately never call onConfirm. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MinuteTimePickerDialog(
    title: String,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initial = minute.coerceIn(0, 1439)
    val state = rememberTimePickerState(initial / 60, initial % 60,
        DateFormat.is24HourFormat(LocalContext.current))
    var input by rememberSaveable { mutableStateOf(false) }
    val largeType = LocalDensity.current.fontScale > 1.3f
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FittedText(title, style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center) {
                    if (input || largeType) TimeInput(state) else TimePicker(state)
                }
                // The switch between keyboard and dial stands centred under the clock.
                if (!largeType) TextButton({ input = !input }, Modifier.align(Alignment.CenterHorizontally).testTag("time-input-toggle")) {
                    Text(if (input) tr("Zifferblatt") else tr("Tastatur"))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text(tr("Abbrechen")) }
                    TextButton({ onConfirm(state.hour * 60 + state.minute) }) { Text(tr("Übernehmen")) }
                }
            }
        }
    }
}
