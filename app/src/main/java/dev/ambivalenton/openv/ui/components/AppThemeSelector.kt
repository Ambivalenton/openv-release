// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import dev.ambivalenton.openv.ui.theme.appThemeBackground
import dev.ambivalenton.openv.ui.theme.appFrame
import dev.ambivalenton.openv.ui.theme.appFrameShadow

/** Uses the same actual palette, typography, radii and texture as the selected app theme. */
@Composable fun AppThemeThumbnail(settings: AppSettings, modifier: Modifier=Modifier) {
    OpenVTheme(settings) {
        // The thumbnail wears the frame of the selected design language.
        val frame=appFrame()
        Surface(modifier.fillMaxWidth().appFrameShadow(frame,MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium),
            color=MaterialTheme.colorScheme.background,
            border=frame.border?:BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
            shape=MaterialTheme.shapes.medium) {
            Column(Modifier.appThemeBackground().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                FittedText(tr("Übersicht"),style=MaterialTheme.typography.titleMedium)
                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary,CircleShape),
                            contentAlignment=Alignment.Center) {
                            AppIcon(R.drawable.ic_home,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onPrimary)
                        }
                        Column(Modifier.weight(1f)) {
                            FittedText(tr("Nach Hause"),style=MaterialTheme.typography.titleSmall)
                            FittedText(tr("12 Min. · Direktverbindung"),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer,MaterialTheme.shapes.large)
                    .padding(6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    // Build 113: three equal slots like the real navigation; a label shrinks, never breaks.
                    FittedText(tr("Übersicht"),Modifier.weight(1f).background(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.shapes.large)
                        .padding(horizontal=6.dp,vertical=8.dp),style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onPrimaryContainer,textAlign=TextAlign.Center,maxLines=1)
                    FittedText(tr("Suchen"),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=1)
                    FittedText(tr("Abfahrten"),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=1)
                }
            }
        }
    }
}

@Composable fun AppThemeSelector(settings: AppSettings,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().testTag("app-theme-selector"),
        shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer,
        contentColor=MaterialTheme.colorScheme.onPrimaryContainer,tonalElevation=3.dp,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.35f))) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_format_paint_outline,null,Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                    FittedText(tr("App-Design"),style=MaterialTheme.typography.titleMedium)
                    Text(settings.appTheme?.title ?: tr("Systemfarben"),style=MaterialTheme.typography.bodySmall)
                }
                AppIcon(R.drawable.ic_chevron_right,tr("App-Design wählen"))
            }
            AppThemeThumbnail(settings)
        }
    }
}
