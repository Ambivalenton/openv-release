// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.saveable.Saver
import dev.ambivalenton.openv.data.model.Location
import dev.ambivalenton.openv.data.repository.StateSerializer

val locationSaver =
    Saver<Location?, String>(
        save = { it?.let { value -> StateSerializer.json.encodeToString(value) } ?: "" },
        restore = {
            if (it.isEmpty()) null
            else runCatching { StateSerializer.json.decodeFromString<Location>(it) }.getOrNull()
        },
    )
