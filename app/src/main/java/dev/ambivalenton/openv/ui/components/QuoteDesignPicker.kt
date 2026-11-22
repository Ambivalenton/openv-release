// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable fun OverviewPresentationSettings(settings:OverviewSettings,onChange:((OverviewSettings)->OverviewSettings)->Unit){
    ToggleRow(tr("Kartenansicht"),tr("Zeigt Verbindungen als kompakte Infokarten statt als Fließtext."),settings.cards){v->onChange {it.copy(cards=v)}}
    ToggleRow(tr("Sprüche & Fakten"),tr("Zeigt Infotexte über deinen Verbindungen. Im Ruhemodus ausgeblendet."),settings.showQuotes){v->onChange {it.copy(showQuotes=v)}}
}
@Composable fun QuoteDesignPicker(selectedDesign:QuoteDecoration,onSelect:(QuoteDecoration)->Unit,onDismiss:()->Unit){
    var group by rememberSaveable{mutableStateOf<QuoteDesignGroup?>(null)}
    DesignPickerWindow(tr("Rahmen & Hintergründe"),"quote-design-picker",onDismiss,false,null){
        item(key="plain"){
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("quote-design-PLAIN")
                .selectable(selectedDesign==QuoteDecoration.PLAIN,role=Role.RadioButton,onClick={onSelect(QuoteDecoration.PLAIN)}).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                Checkbox(selectedDesign==QuoteDecoration.PLAIN,null)
                Text(QuoteDecoration.PLAIN.title,Modifier.padding(start=12.dp),style=MaterialTheme.typography.bodyLarge)
            }
        }
        items(QuoteDesignGroup.entries,key={it.name}){category->
            QuoteGroupCard(category,selectedDesign,paused=group!=null){group=category}
        }
    }
    group?.let{category->
        DesignPickerWindow(category.title,"quote-group-popup",{group=null},false,null){
            item(key="description"){
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(category.description,style=MaterialTheme.typography.bodyMedium)
                    Text(tr("%1\$s Designs",category.designs.size),style=MaterialTheme.typography.labelMedium)
                }
            }
            items(category.designs,key={it.name}){design->
                Box(Modifier.fillMaxWidth().testTag("quote-design-${design.name}")
                    .selectable(selectedDesign==design,role=Role.RadioButton,onClick={onSelect(design)})
                    .then(if(selectedDesign==design)Modifier.border(2.dp,MaterialTheme.colorScheme.primary,appCorner(18.dp))else Modifier).padding(3.dp)){
                    QuoteFrame(design,Modifier.fillMaxWidth()){ink->
                        Column(Modifier.fillMaxWidth().heightIn(min=68.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterVertically)){
                            Text(design.title,color=ink,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
                            Text(design.description,color=ink.copy(alpha=.8f),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
@Composable internal fun QuoteGroupCard(group:QuoteDesignGroup,selected:QuoteDecoration,paused:Boolean,onOpen:()->Unit){
    val designs=group.designs
    var index by rememberSaveable(group){mutableIntStateOf(designs.indexOf(selected).coerceAtLeast(0))}
    var visible by remember{mutableStateOf(false)}
    val calm=LocalAppSettings.current.reduceMotion||!ValueAnimator.areAnimatorsEnabled()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(group,visible,paused,calm,lifecycle){
        if(visible&&!paused&&!calm&&(coroutineContext[MotionDurationScale]?.scaleFactor?:1f)>0f)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(isActive){delay(3_000);if(!ValueAnimator.areAnimatorsEnabled()||(coroutineContext[MotionDurationScale]?.scaleFactor?:1f)<=0f)break;index=(index+1)%designs.size}
        }
    }
    Box(Modifier.fillMaxWidth().height(184.dp).testTag("quote-group-${group.name}")
        .onGloballyPositioned{visible=it.boundsInWindow().let{r->r.width>0&&r.height>0}}
        .clickable(role=Role.Button,onClick=onOpen).clearAndSetSemantics{
            contentDescription=tr("%1\$s · %2\$s Designs",group.title,designs.size)
            onClick(label=tr("Designgruppe öffnen")){onOpen();true}
        }){
        Crossfade(designs[index],animationSpec=tween(if(calm)0 else 850),label="quote-group-style"){sample->
            QuoteFrame(sample,Modifier.fillMaxSize()){ink->
                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                    FittedText(group.title,color=ink,style=MaterialTheme.typography.titleLarge)
                    FittedText(tr("%1\$s Designs",designs.size),color=ink,style=MaterialTheme.typography.labelMedium)
                    FittedText(sample.title,color=ink,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
