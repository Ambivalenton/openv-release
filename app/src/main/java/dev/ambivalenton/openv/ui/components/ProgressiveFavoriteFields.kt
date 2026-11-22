// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr

val primaryFavoriteIcons=listOf(FavoriteIcon.HOME,FavoriteIcon.WORK,FavoriteIcon.SCHOOL,
    FavoriteIcon.FAVORITE,FavoriteIcon.TRAIN,FavoriteIcon.DIRECTIONS_BUS,FavoriteIcon.STOP)

/** The same controlled form is used by the wizard and the ordinary editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProgressiveFavoriteFields(location:Location?,icon:FavoriteIcon?,name:String,nameIsSuggested:Boolean,
    providerKey:String,search:suspend (String)->List<Location>,onLocation:(Location?)->Unit,
    onIcon:(FavoriteIcon)->Unit,onNameEdited:(String)->Unit,tagPrefix:String="favorite",enabled:Boolean=true,
    /** M02: with a suggested name and symbol, both wait behind "Name und Symbol ändern". */
    detailsCollapsible:Boolean=false) {
    val stage=favoriteCreationStage(location,icon)
    var expanded by rememberSaveable {mutableStateOf(false)}
    var detailsOpen by rememberSaveable {mutableStateOf(false)}
    // Collapsed only once a symbol is suggested; without one the choice stays in view.
    val showDetails=!detailsCollapsible || detailsOpen || icon==null
    PlaceSearchField(tr("Adresse oder Haltestelle"),location,providerKey,search,onLocation,
        Modifier.fillMaxWidth().testTag("$tagPrefix-place-search"),enabled=enabled,expressive=true)
    if(detailsCollapsible && stage==FavoriteCreationStage.NAME && icon!=null)
        Surface(Modifier.fillMaxWidth().testTag("$tagPrefix-summary"),shape=MaterialTheme.shapes.large,
            color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),
                itemVerticalAlignment=Alignment.CenterVertically) {
                AppIcon(icon.resource(),null,Modifier.size(24.dp))
                Column(Modifier.weight(1f).widthIn(min=140.dp)) {
                    Text(name,style=MaterialTheme.typography.titleSmall)
                    Text(tr("Symbol · %1\$s",icon.title),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({detailsOpen=!detailsOpen},Modifier.heightIn(min=48.dp).testTag("$tagPrefix-details"),enabled=enabled) {
                    Text(if(detailsOpen) tr("Fertig") else tr("Name und Symbol ändern"))
                }
            }
        }
    CalmVisibility(stage!=FavoriteCreationStage.LOCATION && showDetails,
        enter=expandVertically(spring(dampingRatio=.95f,stiffness=380f))+fadeIn(tween(140)),
        exit=shrinkVertically(tween(140))+fadeOut(tween(100))) {
        Surface(onClick={if(enabled) expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag("$tagPrefix-icon-card"),
            enabled=enabled,shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerHigh,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                FittedText(icon?.let {tr("Symbol · %1\$s",it.title)} ?: tr("Wähle ein Symbol"),Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleMedium)
                @Composable fun choices(icons:List<FavoriteIcon>) {
                    FlowRow(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        icons.forEach {choice->
                            FilterChip(icon==choice,{onIcon(choice)},enabled=enabled,
                                modifier=Modifier.testTag("$tagPrefix-icon-${choice.name}"),label={Text(choice.title)},
                                leadingIcon={AppIcon(choice.resource(),null,Modifier.size(20.dp))})
                        }
                    }
                }
                choices(primaryFavoriteIcons)
                // 2.1: the opened symbols scroll into the visible area (above pill, bars and keyboard).
                val reveal=remember {androidx.compose.foundation.relocation.BringIntoViewRequester()}
                LaunchedEffect(expanded) {if(expanded) {kotlinx.coroutines.delay(180);runCatching {reveal.bringIntoView()}}}
                CalmVisibility(expanded,enter=expandVertically(spring(dampingRatio=.95f,stiffness=380f))+fadeIn(tween(140)),
                    exit=shrinkVertically(tween(140))+fadeOut(tween(100))) {
                    Column(Modifier.bringIntoViewRequester(reveal),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        choices(FavoriteIcon.entries.filter {it !in primaryFavoriteIcons && it!=FavoriteIcon.HEART})
                    }
                }
                // The way to more symbols sits at the lower right, with a small word next to the arrow.
                val rotation by animateFloatAsState(if(expanded) 180f else 0f,calmSpec(spring(dampingRatio=.95f,stiffness=380f)),label="favorite-icons")
                Row(Modifier.align(Alignment.End).testTag("$tagPrefix-expand-icons").clip(MaterialTheme.shapes.small)
                    .clickable(enabled=enabled,role=Role.Button,onClickLabel=tr(if(expanded) "Weniger Symbole anzeigen" else "Weitere Symbole anzeigen")) {expanded=!expanded}
                    .semantics(mergeDescendants=true) {}.padding(start=10.dp,end=2.dp,top=2.dp,bottom=2.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(tr(if(expanded) "Weniger" else "Weitere"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    AppIcon(R.drawable.ic_expand,null,Modifier.size(28.dp).rotate(rotation))
                }
            }
        }
    }
    CalmVisibility(stage==FavoriteCreationStage.NAME && showDetails,
        enter=expandVertically(spring(dampingRatio=.95f,stiffness=380f))+fadeIn(tween(140)),
        exit=shrinkVertically(tween(140))+fadeOut(tween(100))) {
        SuggestedFavoriteName(name,nameIsSuggested,onNameEdited,enabled,
            if(tagPrefix=="onboarding") "onboarding-favorite-name" else "$tagPrefix-name")
    }
}

@Composable private fun SuggestedFavoriteName(name:String,suggested:Boolean,onChange:(String)->Unit,enabled:Boolean,tag:String) {
    var field by rememberSaveable(stateSaver=TextFieldValue.Saver) {mutableStateOf(TextFieldValue(name))}
    LaunchedEffect(name) {
        if(field.text!=name) field=TextFieldValue(name,if(suggested) TextRange(0,name.length) else TextRange(name.length))
    }
    val ink=MaterialTheme.colorScheme.onSurface
    val highContrast=dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current.readability.contrast==AppContrast.HIGH
    val text=if(suggested && !highContrast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.62f) else ink
    OutlinedTextField(field,{next->
        val limited=next.text.take(80)
        val changed=limited!=name
        val selection=if(suggested && !changed) TextRange(0,limited.length) else
            TextRange(next.selection.start.coerceAtMost(limited.length),next.selection.end.coerceAtMost(limited.length))
        field=next.copy(text=limited,selection=selection)
        if(changed) onChange(limited)
    },modifier=Modifier.fillMaxWidth().testTag(tag).onFocusChanged {focus->
        if(focus.isFocused && suggested) field=field.copy(selection=TextRange(0,field.text.length))
    },enabled=enabled,label={Text(tr("Wie soll der Favorit heißen?"))},singleLine=true,shape=MaterialTheme.shapes.medium,
        colors=OutlinedTextFieldDefaults.colors(focusedTextColor=text,unfocusedTextColor=text))
}
