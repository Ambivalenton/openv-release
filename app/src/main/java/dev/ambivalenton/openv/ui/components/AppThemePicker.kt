// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import android.animation.ValueAnimator
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import dev.ambivalenton.openv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppThemePicker(selected:AppTheme?,onSelect:(AppTheme?)->Unit,onDismiss:()->Unit,settings:AppSettings=AppSettings(appTheme=selected),update:(((AppSettings)->AppSettings)->Unit)?=null) {
    var palette by rememberSaveable {mutableStateOf<AppTheme?>(null)}
    val paletteOrigin=remember {PaletteMorphOrigin()}
    val fallback=if(settings.themeMode==ThemeMode.AMOLED)AppTheme.AMOLED_BLACK else if(themeDark(settings,androidx.compose.foundation.isSystemInDarkTheme()))AppTheme.CLASSIC_DARK else AppTheme.CLASSIC_LIGHT
    // 2.10: at night only the dark designs of each group are offered; by day all of them.
    val night=settings.dayNight.night && (settings.dayNight.other!=null || settings.dayNight.schedule!=DayNightSchedule.MANUAL)
    val groups=remember(settings.quietMode,night){groupedAppThemes(settings.quietMode).map {(group,themes)->group to themes.filter {!night || it.dark}}
        .filter {it.second.isNotEmpty()}}
    var group by rememberSaveable{mutableStateOf<AppThemeGroup?>(null)}
    DesignPickerWindow(tr("App-Design wählen"),"app-theme-picker",onDismiss,palette!=null,if(update==null)null else({palette=selected?:fallback}),paletteOrigin) {
        groups.forEach {(category,themes)->
            item(key="group-${category.name}"){
                ThemeGroupHeader(category,themes,if(group!=null)settings.copy(powerSaving=true)else settings,false,selected){group=category}
            }
        }
    }
    group?.let {category->
        val themes=groups.firstOrNull {it.first==category}?.second.orEmpty()
        if(themes.isNotEmpty())DesignPickerWindow(category.title,"app-theme-group-popup",{group=null},palette!=null,
            if(update==null)null else({palette=selected?:fallback}),paletteOrigin){
            item(key="group-description"){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(category.description,style=MaterialTheme.typography.bodyMedium)
                Text(tr("%1\$s Designs",themes.size),style=MaterialTheme.typography.labelMedium)
            }}
            items(themes,key={"theme-${it.name}"}){theme->AppThemeChoice(theme,selected,settings){onSelect(theme)}}
        }
    }
    palette?.let {theme->if(update!=null)AppThemePaletteDialog(settings,theme,{palette=null},update,paletteOrigin)}
}
@Composable internal fun ThemeGroupHeader(group:AppThemeGroup,themes:List<AppTheme>,settings:AppSettings,expanded:Boolean,selected:AppTheme?,onToggle:()->Unit) {
    var sampleIndex by remember(group,settings.quietMode){mutableIntStateOf(themes.indexOf(selected).coerceAtLeast(0))}
    var visible by remember {mutableStateOf(false)}
    val calm=settings.reduceMotion||!ValueAnimator.areAnimatorsEnabled();val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(group,visible,calm,themes,lifecycle){
        if(visible&&!calm&&themes.size>1&&(coroutineContext[MotionDurationScale]?.scaleFactor?:1f)>0f)lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(isActive){delay(3_000);if(!ValueAnimator.areAnimatorsEnabled()||(coroutineContext[MotionDurationScale]?.scaleFactor?:1f)<=0f)break;sampleIndex=(sampleIndex+1)%themes.size}
        }
    }
    Box(Modifier.fillMaxWidth().height(128.dp).testTag("theme-group-${group.name}")
        .onGloballyPositioned {visible=it.boundsInWindow().let {r->r.width>0&&r.height>0}}
        .clickable(role=Role.Button,onClick=onToggle).clearAndSetSemantics {
            heading();role=Role.Button;contentDescription=tr("%1\$s · %2\$s Designs",group.title,themes.size)
            stateDescription=tr("Designgruppe öffnen");onClick(label=tr("Designgruppe öffnen")){onToggle();true}
        }) {
        // Crossfade faded each sample in a layer cut to the card's bounds, so everything
        // outside it – the design's drop shadow, glow or paper layers – was missing during the fade and
        // popped in afterwards. The fading layer now reaches [SAMPLE_SHADOW_ROOM] beyond the card, so
        // design and shadow fade in and out together.
        val transition=updateTransition(themes[sampleIndex%themes.size],label="theme-group-transformation")
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val room=SAMPLE_SHADOW_ROOM
        listOf(transition.currentState,transition.targetState).distinct().forEach {theme->key(theme){
            val alpha by transition.animateFloat({tween(if(calm)0 else 850)},label="theme-group-alpha") {if(it==theme)1f else 0f}
            Box(Modifier.requiredSize(maxWidth+room*2,maxHeight+room*2).graphicsLayer {this.alpha=alpha}
                .padding(room).testTag("theme-group-sample-${theme.name}")) {
            OpenVTheme(settings.copy(appTheme=theme)){
                // The sample is built in its design language (outline, outer effect, inner material);
                // the ten ThemeLanguage collections wear their own frame (rim and shadow).
                val frame=appFrame();val ink=languageInk();val shape=appSurfaceShape(24.dp)
                val primary=MaterialTheme.colorScheme.primary
                Surface(Modifier.fillMaxSize().appFrameShadow(frame,shape).languageShadow(shape,ink),shape=shape,color=ink.fill(MaterialTheme.colorScheme.surfaceContainer),
                    border=if(frame.language!=ThemeLanguage.STANDARD) {if(selected in themes)BorderStroke(2.dp,primary) else frame.border?:BorderStroke(1.dp,primary.copy(alpha=.55f))}
                        else ink.border() ?: BorderStroke(if(selected in themes)2.dp else 1.dp,primary.copy(alpha=.55f))){
                    Row(Modifier.fillMaxSize().appThemeBackground().languageInnerMaterial(shape,ink).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Text(group.title,style=MaterialTheme.typography.titleLarge,maxLines=2)
                            Text(tr("%1\$s Designs",themes.size),style=MaterialTheme.typography.labelMedium)
                            FittedText(if(theme.name.startsWith("VIVALDI_"))theme.title.substringBefore(". ")+"."else theme.title,style=MaterialTheme.typography.labelSmall)
                        };AppIcon(R.drawable.ic_chevron_right,null)
                    }
                }
            }
            }
        }}
        }
    }
}
/** Room around a fading design sample for its outer effects (largest: clay shadow 18 dp + 8 dp offset). */
internal val SAMPLE_SHADOW_ROOM=28.dp
@Composable private fun AppThemeChoice(theme:AppTheme,selected:AppTheme?,settings:AppSettings,onSelect:()->Unit){
    OpenVTheme(settings.copy(appTheme=theme)){
        val frame=appFrame();val ink=languageInk();val shape=appSurfaceShape(20.dp)
        val chosen=selected==theme
        Surface(Modifier.fillMaxWidth().testTag("theme-${theme.name}").appFrameShadow(frame,shape).languageShadow(shape,ink).selectable(chosen,role=Role.RadioButton,onClick=onSelect),
            color=ink.fill(MaterialTheme.colorScheme.surfaceContainer),shape=shape,
            border=if(chosen)BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else frame.border ?: ink.border() ?: BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
            Row(Modifier.appThemeBackground().languageInnerMaterial(shape,ink).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    if(theme.name.startsWith("VIVALDI_")) {
                        FittedText(theme.title.substringBefore(". ")+".",style=MaterialTheme.typography.titleMedium)
                        Text(theme.title.substringAfter(". "),style=MaterialTheme.typography.bodyMedium)
                    } else FittedText(theme.title,style=MaterialTheme.typography.titleLarge)
                    Text(theme.description,style=MaterialTheme.typography.bodySmall)
                    // Design language and typeface as a quiet caption.
                    themeCaption(theme)?.let {Text(it,Modifier.padding(top=4.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    val c=settings.appThemeColors[theme]?:theme.defaultColors();listOf(c.accent,c.secondary).forEach {Box(Modifier.size(18.dp).background(Color(it),CircleShape))}
                }
            }
        }
    }
}

/** "Soft UI · Nunito" – the language and the bundled typeface of a design, or null for Material. */
internal fun themeCaption(theme:AppTheme):String?{
    if(theme.language==DesignLanguage.MATERIAL)return null
    val fonts=listOfNotNull(theme.headFont,theme.bodyFont).distinct().mapNotNull {id->appFonts.firstOrNull {it.id==id}?.name}
    return (listOf(tr(theme.language.titleKey))+fonts).joinToString(" · ")
}
