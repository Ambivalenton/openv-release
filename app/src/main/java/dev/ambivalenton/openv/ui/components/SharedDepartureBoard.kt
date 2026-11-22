// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.theme.cardElevation
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.appFrame
import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import dev.ambivalenton.openv.ui.theme.appPanelDecoration
import dev.ambivalenton.openv.ui.theme.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.*
import dev.ambivalenton.openv.data.model.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.StoredState
import dev.ambivalenton.openv.rendering.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedDepartureBoard(state: StoredState, onStops: () -> Unit, onConfigure: () -> Unit,modifier:Modifier=Modifier,fillHeight:Boolean=false,onJourney:(JourneyQuery,Trip)->Unit={_,_->},onComparison:(JourneyQuery,List<Trip>)->Unit={_,_->},onRowsRequired:(Int)->Unit={}) {
    val clock = rememberWallClock()
    val model =
        boardPresentation(
            state,
            MaterialTheme.colorScheme,
            inApp = true,
            typeface=dev.ambivalenton.openv.ui.theme.AppTypefaces.get(androidx.compose.ui.platform.LocalContext.current,state.settings.fontId),
            themeTypeface=dev.ambivalenton.openv.ui.theme.AppTypefaces.get(androidx.compose.ui.platform.LocalContext.current,state.settings.appTheme?.bodyFace),
            now = clock.toInstant().toEpochMilli(),
        )
    val fontScale = LocalDensity.current.fontScale
    val pixelDensity = LocalDensity.current.density
    // H11: every row compares with its own query (destination and filter); the board as a whole only
    // when all linked rows belong to one clearly named group.
    val groups=remember(model.rows) {boardComparisonGroups(model.rows)}
    fun compare(row:Departure) {
        val query=row.journeyQuery ?: return
        val gallery=boardJourneyGallery(model.rows,query)
        if(gallery.isNotEmpty()) onComparison(query,gallery)
    }
    val single=groups.singleOrNull()
    // "App-Theme" is part of the screen, not a picture of a board: a real card of the app.
    if(model.design==BoardDesign.APP_INTEGRATED) {
        IntegratedDepartureBoard(model,onStops,onConfigure,modifier,fillHeight,onJourney,groups,::compare,onRowsRequired)
        return
    }
    val cardShape=dev.ambivalenton.openv.ui.theme.appCorner(24.dp)
    AppCard(modifier=modifier.testTag("shared-departure-board").then(if(single!=null)Modifier.combinedClickable(
        onClick={},onLongClickLabel=single.label,onLongClick={compare(single.first)}) else Modifier),shape = cardShape,
        colors=if(model.appRoles!=null) CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(model.colors.background.toInt())) else CardDefaults.cardColors()) {
        BoxWithConstraints(Modifier.fillMaxWidth().then(if(fillHeight)Modifier.weight(1f) else Modifier)) {
            val boardWidth=maxWidth
            val standard=304f * fontScale.coerceIn(1f,1.6f)
            val sample=model.layout(boardWidth.value,standard,fontScale)
            val linked=model.rows.count {it.journey!=null && it.journeyQuery!=null}
            val needed=if(linked>3)(sample.padding*2+sample.header+sample.footer+sample.rowHeight*minOf(6,linked)+1f) else standard
            val height=if(fillHeight)maxOf(needed,maxHeight.value.coerceAtLeast(40f)) else maxOf(standard,needed)
            val capacity=boardLayout(boardWidth.value,height,fontScale,journeyRows=true).rows.coerceIn(6,30)
            LaunchedEffect(capacity) {onRowsRequired((capacity+2).coerceAtMost(30))}
            Column(Modifier.then(if(fillHeight && linked>0)Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Box {
            val bitmap =
                remember(model, boardWidth, height, fontScale, pixelDensity) {
                    BoardRenderer.render(model, boardWidth.value, height, fontScale, density = pixelDensity)
                }
            // M09: the picture is decoration; the rows on top of it carry one readable focus each.
            Image(
                bitmap.asImageBitmap(),
                null,
                Modifier.fillMaxWidth().height(height.dp).testTag("shared-departure-board-image").clearAndSetSemantics {},
                contentScale = ContentScale.FillBounds,
            )
            val layout=model.layout(boardWidth.value,height,fontScale)
            model.rows.take(layout.rows).forEachIndexed {index,row->
                val described=boardRowDescription(row)
                val opens=row.journey!=null && row.journeyQuery!=null
                val group=groups.firstOrNull {it.matches(row)}
                Box(
                    Modifier.offset(y=(layout.padding+layout.header+index*layout.rowHeight).dp)
                        .fillMaxWidth().height(layout.rowHeight.dp.coerceAtLeast(48.dp))
                        .then(if(opens) Modifier.testTag("board-journey-$index")
                            .combinedClickable(role=Role.Button,onClickLabel=tr("Zeitstrahl öffnen"),onClick={onJourney(row.journeyQuery!!,row.journey!!)},
                                onLongClickLabel=group?.label,onLongClick=group?.let {{compare(row)}})
                        else Modifier.testTag("board-row-$index"))
                        .clearAndSetSemantics {
                            contentDescription=described
                            if(group!=null) customActions=listOf(CustomAccessibilityAction(group.label) {compare(row);true})
                        })
            }
        }
            }
            }
        TransitSourceAttribution(Modifier.padding(horizontal=16.dp),compact=true)
        // H11/M09: the comparison of each group named in words, and the list view of the same rows.
        BoardGroupActions(groups,::compare)
        DeparturesFooter(onStops, onConfigure)
    }
}

/** H11: one comparison group of the board: the rows with the same query (destination and filter). */
data class BoardComparisonGroup(val query:JourneyQuery,val destination:String,val first:Departure) {
    val label:String get()=tr("Weitere Fahrten nach %1\$s",destination)
    fun matches(row:Departure)=row.journeyQuery?.copy(time=query.time)==query
}

/** H11: the distinct groups in board order (pure, unit-tested). */
fun boardComparisonGroups(rows:List<Departure>):List<BoardComparisonGroup> {
    val groups=mutableListOf<BoardComparisonGroup>()
    for(row in rows) {
        val query=row.journeyQuery ?: continue
        if(row.journey==null || groups.any {it.matches(row)}) continue
        if(boardJourneyGallery(rows,query).isEmpty()) continue
        groups+=BoardComparisonGroup(query,query.destination.name,row)
    }
    return groups
}

/** M09: what TalkBack reads for one departure: time or status, line, destination, platform and delay. */
fun boardRowDescription(row:Departure):String {
    val time=if(row.delay.cancelled) tr("Fällt aus") else clockTime(row.time)
    val late=row.delay.minutes?.takeIf {it>0 && !row.delay.cancelled}?.let {tr("%1\$s Minuten später",it)}
    return listOfNotNull(time,tr("%1\$s nach %2\$s",row.lineName,row.destination),platformName(row.platform,row.lineType),late,
        if(row.delay.cancelled) tr("planmäßig %1\$s",clockTime(row.plannedTime)) else null).joinToString(", ")
}

/** H11: "Weitere Fahrten nach [Ziel]" as labelled actions (at most three groups). */
@Composable internal fun BoardGroupActions(groups:List<BoardComparisonGroup>,onCompare:(Departure)->Unit) {
    if(groups.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal=8.dp).testTag("board-group-actions")) {
        groups.take(3).forEach {group->
            TextButton({onCompare(group.first)},Modifier.heightIn(min=48.dp)) {Text(group.label,maxLines=2)}
        }
    }
}


/** Rows the card shows outside the full-height board (the former picture showed six as well). */
internal const val INTEGRATED_BOARD_ROWS=6

/**
 * "App-Theme" board in the app's own components: the same card, corners, panel material,
 * typography and colour roles as the "In deiner Nähe" cards, crisp at every density. Line badges
 * take tones of the theme instead of the transport colours, so nothing looks attached.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun IntegratedDepartureBoard(model:BoardPresentation,onStops:()->Unit,onConfigure:()->Unit,modifier:Modifier,
    fillHeight:Boolean,onJourney:(JourneyQuery,Trip)->Unit,groups:List<BoardComparisonGroup>,onCompare:(Departure)->Unit,onRowsRequired:(Int)->Unit) {
    val cardShape=dev.ambivalenton.openv.ui.theme.appCorner(28.dp)
    val rows=if(fillHeight) model.rows else model.rows.take(INTEGRATED_BOARD_ROWS)
    LaunchedEffect(fillHeight) {onRowsRequired(if(fillHeight) 30 else INTEGRATED_BOARD_ROWS+2)}
    val single=groups.singleOrNull()
    AppCard(modifier=modifier.testTag("shared-departure-board")
        .then(if(single!=null)Modifier.combinedClickable(onClick={},onLongClickLabel=single.label,onLongClick={onCompare(single.first)}) else Modifier),
        shape=cardShape,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().then(if(fillHeight) Modifier.weight(1f) else Modifier).appPanelDecoration(cardShape)) {
            Column(Modifier.fillMaxWidth().then(if(fillHeight) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                .padding(start=16.dp,end=16.dp,top=16.dp,bottom=4.dp).testTag("shared-departure-board-rows"),
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min=40.dp).padding(bottom=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    AppIcon(R.drawable.ic_train,null,tint=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    FittedText(model.title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleMedium)
                }
                if(rows.isEmpty()) Text(model.emptyMessage,Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                rows.forEachIndexed {index,row->IntegratedBoardRow(row,index,onJourney,groups.firstOrNull {it.matches(row)}?.let {group->group to {onCompare(row)}})}
                Text(model.caption,Modifier.padding(top=6.dp),style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TransitSourceAttribution(Modifier.padding(horizontal=16.dp),compact=true)
            BoardGroupActions(groups,onCompare)
            DeparturesFooter(onStops,onConfigure)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun IntegratedBoardRow(row:Departure,index:Int,onJourney:(JourneyQuery,Trip)->Unit,compare:Pair<BoardComparisonGroup,()->Unit>?) {
    val scheme=MaterialTheme.colorScheme
    val rowShape=dev.ambivalenton.openv.ui.theme.appCorner(16.dp)
    val opens=row.journey!=null && row.journeyQuery!=null
    val cancelled=row.delay.cancelled
    val late=row.delay.minutes?.takeIf {it>0 && !cancelled}
    val detail=row.platform?.let {platformName(it,row.lineType)} ?: row.lineType.label
    val described=boardRowDescription(row)
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clip(rowShape)
        .then(if(opens) Modifier.testTag("board-journey-$index").combinedClickable(role=Role.Button,
            onClickLabel=tr("Zeitstrahl öffnen"),onClick={onJourney(row.journeyQuery!!,row.journey!!)},
            onLongClickLabel=compare?.first?.label,onLongClick=compare?.second) else Modifier.testTag("board-row-$index"))
        // M09: one focus per row with time, destination, platform and status.
        .clearAndSetSemantics {
            contentDescription=described
            compare?.let {(group,action)->customActions=listOf(CustomAccessibilityAction(group.label) {action();true})}
        }
        .padding(horizontal=4.dp,vertical=6.dp),
        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        IntegratedLineBadge(row.lineName,row.lineType)
        Column(Modifier.weight(1f)) {
            Text(row.destination,style=MaterialTheme.typography.bodyLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(detail,style=MaterialTheme.typography.labelMedium,color=scheme.onSurfaceVariant,maxLines=1,
                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment=androidx.compose.ui.Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(if(cancelled) tr("Fällt aus") else clockTime(row.time),style=MaterialTheme.typography.titleMedium,
                fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=if(cancelled) scheme.error else scheme.primary,maxLines=1)
            if(late!=null) Surface(color=scheme.errorContainer,contentColor=scheme.onErrorContainer,shape=dev.ambivalenton.openv.ui.theme.appCorner(8.dp)) {
                Text(tr("+%1\$s Min.",late),Modifier.padding(horizontal=6.dp,vertical=1.dp),style=MaterialTheme.typography.labelSmall,maxLines=1)
            }
        }
        if(opens) AppIcon(R.drawable.ic_chevron_right,null,Modifier.size(18.dp),tint=scheme.onSurfaceVariant)
    }
}

/** Theme tones per kind of transport: rail, urban rail, road and water stay distinguishable. */
internal enum class BoardBadgeTone {PRIMARY,SECONDARY,TERTIARY,NEUTRAL}
internal fun boardBadgeTone(type:LineType):BoardBadgeTone=when(type) {
    LineType.ICE,LineType.REGIONAL,LineType.SUBURBAN->BoardBadgeTone.PRIMARY
    LineType.SUBWAY,LineType.TRAM->BoardBadgeTone.SECONDARY
    LineType.BUS,LineType.FERRY->BoardBadgeTone.TERTIARY
    LineType.WALK,LineType.OTHER->BoardBadgeTone.NEUTRAL
}

@Composable private fun IntegratedLineBadge(name:String,type:LineType) {
    val scheme=MaterialTheme.colorScheme
    val (fill,ink)=when(boardBadgeTone(type)) {
        BoardBadgeTone.PRIMARY->scheme.primaryContainer to scheme.onPrimaryContainer
        BoardBadgeTone.SECONDARY->scheme.secondaryContainer to scheme.onSecondaryContainer
        BoardBadgeTone.TERTIARY->scheme.tertiaryContainer to scheme.onTertiaryContainer
        BoardBadgeTone.NEUTRAL->scheme.surfaceContainerHighest to scheme.onSurface
    }
    val lang=dev.ambivalenton.openv.ui.theme.languageInk()
    Surface(color=fill,contentColor=ink,shape=dev.ambivalenton.openv.ui.theme.appCorner(10.dp),border=with(lang) {border()}) {
        Text(name,Modifier.widthIn(min=46.dp,max=84.dp).padding(horizontal=8.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge,
            fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
