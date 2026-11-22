// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.onboarding.mordorOverviewExample
import dev.ambivalenton.openv.ui.theme.appCorner

internal data class OverviewDesignExample(val entry:OverviewEntry?,val fictional:Boolean)
/** Preview the first actual overview row. Fiction is used only when no favourite exists. */
internal fun overviewDesignExample(store:StoredState,overview:OverviewUiState):OverviewDesignExample {
    if(store.favorites.isEmpty()){
        val (favorite,trip)=mordorOverviewExample()
        return OverviewDesignExample(OverviewEntry(favorite,0,trip=trip,loading=false),true)
    }
    val entry=overview.entries.firstOrNull()
    return OverviewDesignExample(entry?:if(!overview.ready)OverviewEntry(store.favorites.first(),0)else null,false)
}
@Composable internal fun OverviewDesignSettings(store:StoredState,overview:OverviewUiState,onRefresh:()->Unit={},update:((AppSettings)->AppSettings)->Unit){
    val app=store.settings;val settings=app.overview
    var picker by rememberSaveable{mutableStateOf(false)}
    val example=remember(store.favorites,store.network,store.settings.originMode,store.settings.manualOrigin,overview){overviewDesignExample(store,overview)}
    Surface(Modifier.fillMaxWidth().testTag("overview-design-preview"),shape=appCorner(24.dp),
        color=MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                if(settings.showQuotes && !app.quietMode && !app.minimalistMode)QuoteFrame(settings.quoteDecoration,Modifier.fillMaxWidth().testTag("overview-preview-frame").clickable(role=Role.Button){picker=true}){ink->
                    Box(Modifier.fillMaxWidth().heightIn(min=76.dp),contentAlignment=Alignment.Center){
                        FittedText(tr("Rahmen ändern"),Modifier.fillMaxWidth(),color=ink,
                            style=MaterialTheme.typography.headlineSmall.copy(textAlign=TextAlign.Center))
                    }
                }
            OverviewDottedDivider(isLoading=false)
            val entry=example.entry
            if(entry!=null){
                if(settings.cards)OverviewConnectionCard(entry,settings,{},Modifier.testTag("overview-preview-card"))
                else OverviewPreviewParagraph(entry,settings,app)
                entry.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium);TextButton(onRefresh){Text(tr("Erneut versuchen"))}}
            }else Text(overview.error?:tr("Aktuell sind keine Favoriten in deiner Übersicht sichtbar."),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(example.fictional)Text(tr("Fiktives Beispiel · Reise nach Mordor"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    OverviewPresentationSettings(settings){change->update{it.copy(overview=change(it.overview))}}
    SettingsDisclosure(tr("Wähle, welche Angaben die Übersicht zeigt."),"overview-detail-options"){
        OverviewSentenceFields(settings){change->update{it.copy(overview=change(it.overview))}}
    }
    if(picker)QuoteDesignPicker(settings.quoteDecoration,{design->update{it.copy(overview=it.overview.copy(quoteDecoration=design))};picker=false},{picker=false})
}
/**
 * "Übersicht-Design" on the design page like the other design entries: a framed card with
 * title, the current presentation and a preview of the first overview row; it opens the page.
 */
@Composable internal fun OverviewDesignSelector(store:StoredState,overview:OverviewUiState,onClick:()->Unit){
    val app=store.settings;val settings=app.overview
    val example=remember(store.favorites,store.network,store.settings.originMode,store.settings.manualOrigin,overview){overviewDesignExample(store,overview)}
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().testTag("overview-design-selector"),shape=MaterialTheme.shapes.large,
        color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.35f))){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                AppIcon(R.drawable.ic_overview,null,Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                    FittedText(tr("Übersicht-Design"),style=MaterialTheme.typography.titleMedium)
                    Text(if(settings.cards)tr("Kartenansicht") else tr("Fließtext"),style=MaterialTheme.typography.bodySmall)
                }
                AppIcon(R.drawable.ic_chevron_right,tr("Übersicht-Design öffnen"))
            }
            // The preview sits on the app's own background, as the overview itself.
            Surface(Modifier.fillMaxWidth().testTag("overview-design-selector-preview"),shape=appCorner(18.dp),
                color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onSurface){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    if(settings.showQuotes && !app.quietMode && !app.minimalistMode)QuoteFrame(settings.quoteDecoration,Modifier.fillMaxWidth()){ink->
                        Box(Modifier.fillMaxWidth().heightIn(min=40.dp),contentAlignment=Alignment.Center){
                            FittedText(tr("Sprüche & Fakten"),Modifier.fillMaxWidth(),color=ink,
                                style=MaterialTheme.typography.titleSmall.copy(textAlign=TextAlign.Center))
                        }
                    }
                    val entry=example.entry
                    if(entry!=null){
                        if(settings.cards)OverviewConnectionCard(entry,settings,{},Modifier)
                        else OverviewPreviewParagraph(entry,settings,app)
                    }else Text(tr("Aktuell sind keine Favoriten in deiner Übersicht sichtbar."),style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable private fun OverviewPreviewParagraph(entry:OverviewEntry,settings:OverviewSettings,app:AppSettings){
    val size=OVERVIEW_BODY_SIZE*app.readability.normalized().textScale
    val trip=entry.trip
    val parts=if(trip!=null)OverviewSentenceGenerator.sentence(entry.favorite,trip,settings,entry.variant,entry.weather,entry.network?.anchorIcon)
        else listOf(OverviewTextPart(if(entry.loading)tr("Die nächste Verbindung zu ")else tr("Für dein Ziel ")),
            OverviewTextPart(entry.favorite.label,OverviewEmphasis.PLACE),OverviewTextPart(if(entry.loading)tr(" wird gesucht …")else tr(" ist noch keine Verbindung verfügbar.")))
    val highlights=overviewFavoriteHighlights(entry.favorite.accentSeed)
    Text(overviewAnnotatedString(parts,highlights.original,baseSize=size,mutedAccent=highlights.tonal,originAccent=overviewFavoriteHighlights(if(entry.favorite.counterpole!=null)entry.favorite.originAccentSeed else entry.favorite.accentSeed).original),
        Modifier.fillMaxWidth().testTag("overview-preview-paragraph"),style=MaterialTheme.typography.bodyLarge.copy(fontSize=size,lineHeight=OVERVIEW_LINE_HEIGHT*app.readability.normalized().textScale),color=MaterialTheme.colorScheme.onSurface)
}
