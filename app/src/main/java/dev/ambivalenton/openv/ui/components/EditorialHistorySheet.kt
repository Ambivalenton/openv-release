// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable fun EditorialHistorySheet(rows:List<SeenEditorialText>,initialKind:JourneyContentKind?,onDismiss:()->Unit) {
    var factsOpen by rememberSaveable {mutableStateOf(initialKind==JourneyContentKind.FACT)}
    var sayingsOpen by rememberSaveable {mutableStateOf(initialKind!=null && initialKind!=JourneyContentKind.FACT && initialKind!=JourneyContentKind.TIP)}
    var tipsOpen by rememberSaveable {mutableStateOf(initialKind==JourneyContentKind.TIP)}
    val (facts,sayings)=remember(rows) {editorialHistoryGroups(rows.filter {it.kind!=JourneyContentKind.TIP})}
    val tips=remember(rows){rows.filter {it.kind==JourneyContentKind.TIP}.sortedByDescending {it.lastShownAt}}
    AppModalBottomSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.85f)
            .testTag("editorial-history")) {
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                FittedText(tr("Deine Textchronik"),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                IconButton(onDismiss,Modifier.testTag("editorial-history-close")) {AppIcon(R.drawable.ic_close,tr("Textchronik schließen"))}
            }
            if(rows.isEmpty()) Text(tr("Hier erscheinen die Texte, die du in der Übersicht gesehen hast."),Modifier.padding(20.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("editorial-history-list"),
                contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                stickyHeader("facts") {HistoryDisclosure(tr("Fakten"),facts.size,factsOpen,{factsOpen=!factsOpen},"history-facts")}
                if(factsOpen) items(facts,key={it.key}) {HistoryEntry(it)}
                stickyHeader("sayings") {HistoryDisclosure(tr("Sprüche"),sayings.size,sayingsOpen,{sayingsOpen=!sayingsOpen},"history-sayings")}
                if(sayingsOpen) items(sayings,key={it.key}) {HistoryEntry(it)}
                stickyHeader("tips") {HistoryDisclosure(tr("Bedienungstipps"),tips.size,tipsOpen,{tipsOpen=!tipsOpen},"history-tips")}
                if(tipsOpen) items(tips,key={it.key}) {HistoryEntry(it)}
            }
        }
    }
}

@Composable private fun HistoryDisclosure(title:String,count:Int,expanded:Boolean,onClick:()->Unit,tag:String) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag).clickable(onClick=onClick)
        .semantics {heading();stateDescription=tr(if(expanded) "Ausgeklappt" else "Eingeklappt")},verticalAlignment=Alignment.CenterVertically) {
        FittedText(tr("%1\$s · %2\$s",title,count),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        AppIcon(R.drawable.ic_expand,null,Modifier.rotate(if(expanded) 180f else 0f))
    }
    }
}

@Composable private fun HistoryEntry(entry:SeenEditorialText) {
    Surface(Modifier.fillMaxWidth().testTag("history-entry-${entry.contentId}"),shape=MaterialTheme.shapes.medium,
        color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(entry.text,style=MaterialTheme.typography.bodyLarge)
            val date=remember(entry.lastShownAt) {Instant.ofEpochMilli(entry.lastShownAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT))}
            Text(date,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
