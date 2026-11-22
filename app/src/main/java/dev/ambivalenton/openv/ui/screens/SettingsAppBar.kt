// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appSystemHeader
import dev.ambivalenton.openv.ui.theme.appTopBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.ui.components.PaletteAuraIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsAppBar(navigation:SettingsNavigationState,onExit:()->Unit,onQuietChange:((Boolean)->Unit)?=null,personalThemeUi:PersonalThemeUiState?=null,
    settings:dev.ambivalenton.openv.data.model.AppSettings?=null,onDayNight:(()->Unit)?=null) {
    Column {
        TopAppBar(modifier=Modifier.appSystemHeader().testTag("settings-app-bar"),colors=appTopBarColors(),
            // 2.10: "Tag & Nacht" sits where the save button was; saving moved into "Persönliche Themes".
            actions={if(navigation.page==SettingsPage.DESIGN&&settings!=null&&onDayNight!=null)
                dev.ambivalenton.openv.ui.components.DayNightButton(settings,onDayNight,Modifier.padding(end=8.dp))},
            title={dev.ambivalenton.openv.ui.components.FittedText(tr(navigation.page.titleKey),Modifier.testTag("settings-title"))},
            navigationIcon={IconButton({if(!navigation.back())onExit()},Modifier.testTag("settings-back")) {AppIcon(R.drawable.ic_back,tr("Zurück"))}})
    }
}
