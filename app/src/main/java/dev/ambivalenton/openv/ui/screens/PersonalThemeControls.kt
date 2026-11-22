// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.*

@Stable class PersonalThemeUiState{
    var dialogOpen by mutableStateOf(false)
    var draft by mutableStateOf("")
    var expanded by mutableStateOf(false)
    var deferFirstInsertion by mutableStateOf(false)
    var committed by mutableStateOf(false)
    var revealTick by mutableIntStateOf(0)
    var buttonBounds by mutableStateOf<Rect?>(null)
    var menuBounds by mutableStateOf<Rect?>(null)
    fun requestSave(){draft="";committed=false;dialogOpen=true}
    fun dismiss(){dialogOpen=false;deferFirstInsertion=false;committed=false}
}
@Composable fun rememberPersonalThemeUiState()=rememberSaveable(saver=listSaver(
    save={listOf(it.dialogOpen&&!it.committed,it.draft,it.expanded)},
    restore={PersonalThemeUiState().apply{dialogOpen=it[0]as Boolean;draft=it[1]as String;expanded=it[2]as Boolean}}
)){PersonalThemeUiState()}

@Composable fun PersonalThemeSaveButton(state:PersonalThemeUiState,modifier:Modifier=Modifier.padding(end=8.dp)){
    val view=LocalView.current
    IconButton(state::requestSave,modifier.size(48.dp)
        .onGloballyPositioned{c->val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
            state.buttonBounds=c.boundsInWindow().translate((screen[0]-window[0]).toFloat(),(screen[1]-window[1]).toFloat())}
        .graphicsLayer{alpha=if(state.dialogOpen)0f else 1f}
        .background(MaterialTheme.colorScheme.primaryContainer,CircleShape).testTag("save-personal-theme")){
        AppIcon(R.drawable.ic_save,tr("Persönliches Theme speichern"),tint=MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable internal fun PersonalThemesMenu(store:StoredState,state:PersonalThemeUiState,currentLogo:AppLogo,
    onApply:(PersonalTheme)->Unit,onDelete:(String)->Unit){
    // 2.9: the menu holds the save button, so it is always there (no first-save reveal any more).
    val show=true
    val calm=store.settings.reduceMotion
    val bring=remember{BringIntoViewRequester()};val view=LocalView.current
    LaunchedEffect(show,state.revealTick){if(show&&state.revealTick>0)bring.bringIntoView()}
    AnimatedVisibility(show,enter=expandVertically(tween(if(calm)0 else 650),expandFrom=Alignment.Top)+fadeIn(tween(if(calm)0 else 400)),
        exit=shrinkVertically(tween(if(calm)0 else 200))){
        Surface(Modifier.fillMaxWidth().testTag("personal-themes-menu"),shape=appCorner(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
            Column(Modifier.appPanelDecoration(appCorner(22.dp))){
                Row(Modifier.fillMaxWidth().bringIntoViewRequester(bring).onGloballyPositioned{c->
                    val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
                    state.menuBounds=c.boundsInWindow().translate((screen[0]-window[0]).toFloat(),(screen[1]-window[1]).toFloat())
                }.padding(start=8.dp),verticalAlignment=Alignment.CenterVertically){
                    // 2.9: the floppy disk is the save button with its own touch area (the space around
                    // it saves as well); folding belongs to the title and the arrow beside it.
                    Box(Modifier.size(64.dp).clickable(role=Role.Button,onClickLabel=tr("Persönliches Theme speichern"),
                        onClick=state::requestSave).testTag("save-personal-theme-area"),contentAlignment=Alignment.Center){
                        PersonalThemeSaveButton(state,Modifier)
                    }
                    Row(Modifier.weight(1f).clickable(role=Role.Button,onClickLabel=tr(if(state.expanded)"Persönliche Themes einklappen" else "Persönliche Themes ausklappen")){state.expanded=!state.expanded}
                        .semantics{stateDescription=if(state.expanded)tr("Ausgeklappt") else tr("Eingeklappt")}
                        .padding(top=16.dp,bottom=16.dp,end=16.dp).testTag("personal-themes-toggle"),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f).padding(horizontal=8.dp)){
                        FittedText(tr("Persönliche Themes"),style=MaterialTheme.typography.titleMedium)
                        Text(tr("%1\$s gespeichert",store.personalThemes.size),style=MaterialTheme.typography.bodySmall)
                    }
                    val angle by animateFloatAsState(if(state.expanded)180f else 0f,tween(if(calm)0 else 280),label="personal-menu-chevron")
                    AppIcon(R.drawable.ic_expand,null,Modifier.graphicsLayer{rotationZ=angle})
                    }
                }
                AnimatedVisibility(state.expanded,enter=expandVertically(tween(if(calm)0 else 350),expandFrom=Alignment.Top)+fadeIn(tween(if(calm)0 else 200)),exit=shrinkVertically(tween(if(calm)0 else 200))+fadeOut(tween(if(calm)0 else 160))){
                    Column(Modifier.padding(start=12.dp,end=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        if(store.personalThemes.isEmpty())Text(tr("Speichere dein aktuelles Design mit der Diskette links."),style=MaterialTheme.typography.bodyMedium)
                        store.personalThemes.asReversed().forEach{theme->
                            key(theme.id){PersonalThemeRow(theme,theme.design.matches(store.settings.personalDesign(currentLogo)),
                                store.settings,onApply={onApply(theme)},onDelete={onDelete(theme.id)})}
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun PersonalThemeRow(theme:PersonalTheme,selected:Boolean,current:AppSettings,onApply:()->Unit,onDelete:()->Unit){
    OpenVTheme(current.withPersonalDesign(theme.design)){
        Surface(Modifier.fillMaxWidth().testTag("personal-theme-${theme.id}").clickable(role=Role.Button,onClick=onApply),
            shape=appCorner(18.dp),color=MaterialTheme.colorScheme.surfaceContainer,
            border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)){
            Row(Modifier.appThemeBackground().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                NativeAppLogoImage(theme.design.logo,42,null)
                Column(Modifier.weight(1f)){
                    Text(theme.name,style=MaterialTheme.typography.titleMedium)
                    Text(theme.design.appearance.appTheme?.title?:tr("Eigenes Design"),style=MaterialTheme.typography.bodySmall)
                }
                IconButton(onDelete,Modifier.size(48.dp)){AppIcon(R.drawable.ic_delete,tr("Theme %1\$s löschen",theme.name))}
            }
        }
    }
}
