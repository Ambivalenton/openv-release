// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.components.*

@Composable internal fun TimelineDesignSettings(state:StoredState,vm:AppViewModel) {
    var picker by rememberSaveable {mutableStateOf(false)}
    TimelineDesignSelector(state.settings) {picker=true}
    if(picker) TimelineDesignPicker(state.settings,{theme->vm.settings {it.selectTimelineTheme(theme)}},
        {picker=false},vm::settings)
}
