// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.hints.gestureHint

import dev.ambivalenton.openv.ui.components.AppDatePickerDialog as DatePickerDialog

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import java.time.*
import java.time.format.DateTimeFormatter

internal fun pickerDateMillis(time: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(time)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

internal fun combinePickerDate(dateUtc: Long, previousTime: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(dateUtc)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atTime(
            Instant.ofEpochMilli(previousTime).atZone(zone).toLocalTime().withSecond(0).withNano(0)
        )
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

/** One visual control, two independent accessible targets. Material's date is UTC-midnight. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitDateTimePicker(
    timeMillis: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val shown = Instant.ofEpochMilli(timeMillis ?: System.currentTimeMillis()).atZone(zone)
    Surface(
        // Build 115: the date and time can be changed – a quiet pulse in the first hours of use.
        modifier.fillMaxWidth().gestureHint(dev.ambivalenton.openv.data.model.GestureHintKeys.SEARCH_TIME, dev.ambivalenton.openv.ui.hints.HintKind.PULSE, 3400L),
        shape = dev.ambivalenton.openv.ui.theme.appCorner(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            PickerHalf(
                tr("Datum wählen"),
                if (shown.toLocalDate() == LocalDate.now(zone)) tr("Heute")
                else shown.format(DateTimeFormatter.ofPattern(tr("dd. MMM"))),
                R.drawable.ic_calendar,
                Modifier.weight(1f),
            ) {
                dialog = "date"
            }
            VerticalDivider(
                Modifier.height(28.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            PickerHalf(
                tr("Uhrzeit wählen"),
                if (timeMillis == null) tr("Jetzt")
                else shown.format(DateTimeFormatter.ofPattern("HH:mm")),
                R.drawable.ic_clock,
                Modifier.weight(1f),
                // The clock face shows the chosen time, or the current one for "Jetzt".
                leading = { AnalogClockIcon(timeMillis, Modifier.size(20.dp)) },
            ) {
                dialog = "time"
            }
        }
    }
    if (dialog == "date") {
        val previous = timeMillis ?: System.currentTimeMillis()
        val state =
            rememberDatePickerState(initialSelectedDateMillis = pickerDateMillis(previous, zone))
        DatePickerDialog(
            onDismissRequest = { dialog = null },
            confirmButton = {
                TextButton(
                    {
                        state.selectedDateMillis?.let {
                            onChange(combinePickerDate(it, previous, zone))
                        }
                        dialog = null
                    },
                    enabled = state.selectedDateMillis != null,
                ) {
                    Text(tr("Übernehmen"))
                }
            },
            dismissButton = { TextButton({ dialog = null }) { Text(tr("Abbrechen")) } },
        ) {
            DatePicker(state, showModeToggle = true)
        }
    }
    if (dialog == "time") {
        val local = Instant.ofEpochMilli(timeMillis ?: System.currentTimeMillis()).atZone(zone)
        MinuteTimePickerDialog(
            title = tr("Uhrzeit wählen"),
            minute = local.hour * 60 + local.minute,
            onDismiss = { dialog = null },
        ) { minute ->
            onChange(local.toLocalDate().atTime(minute / 60, minute % 60)
                .atZone(zone).toInstant().toEpochMilli())
            dialog = null
        }
    }
}

@Composable
private fun PickerHalf(
    description: String,
    value: String,
    icon: Int,
    modifier: Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else AppIcon(icon, null, Modifier.size(20.dp))
        FittedText(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
