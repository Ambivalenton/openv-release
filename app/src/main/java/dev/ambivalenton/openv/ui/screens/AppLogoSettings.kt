// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.BorderStroke
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.AppLogo
import dev.ambivalenton.openv.data.model.availableAppLogos
import dev.ambivalenton.openv.launcher.LauncherIconManager
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun AppLogoSettings(manager:LauncherIconManager?=null,onChange:(suspend (AppLogo)->Result<AppLogo>)?=null) {
    val context=LocalContext.current
    val icons=remember(manager,context){manager ?: LauncherIconManager(context)}
    var selected by remember(icons){mutableStateOf(icons.current())}
    var busy by remember {mutableStateOf(false)}
    var pending by remember {mutableStateOf<AppLogo?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().testTag("app-logo-settings"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(tr("Dein Logo auf dem Startbildschirm"),style=MaterialTheme.typography.titleMedium)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            NativeAppLogoImage(selected,88,selected.title,Modifier.testTag("app-logo-current"))
            Column(Modifier.weight(1f)) {
                Text(selected.title,style=MaterialTheme.typography.titleMedium)
                Text(selected.description,style=MaterialTheme.typography.bodyMedium)
            }
        }
        Text(tr("Wähle dein App-Logo. Der Wechsel gilt für das Symbol im Launcher und bleibt unabhängig vom App-Theme erhalten."),style=MaterialTheme.typography.bodyMedium)
        Text(tr("Manche Launcher aktualisieren Symbole mit etwas Verzögerung. Android-Designsymbole können die Farben an dein System anpassen. Bereits angeheftete Verknüpfungen können ihr bisheriges Symbol behalten."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth<300.dp)2 else 3
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                availableAppLogos.chunked(columns).forEach {row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        row.forEach {logo->
                            val chosen=selected==logo
                            Surface(Modifier.weight(1f).heightIn(min=136.dp).testTag("app-logo-${logo.name}")
                                .selectable(chosen,enabled=!busy,role=Role.RadioButton,onClick={
                                    if(!chosen)pending=logo
                                }),shape=appCorner(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
                                border=BorderStroke(if(chosen)2.dp else 1.dp,if(chosen)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    NativeAppLogoImage(logo,64,null)
                                    Text(logo.title,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,maxLines=2)
                                    if(chosen)Text(tr("Ausgewählt"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                    }
                }
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().semantics {contentDescription=tr("App-Logo wird gewechselt")})
    }
    pending?.let{logo->AlertDialog(onDismissRequest={pending=null},title={Text(tr("App-Logo ändern?"))},
        text={Text(tr("Der Launcher übernimmt dein neues Symbol. Auf manchen Geräten kann Android die App dabei kurz neu öffnen. Deine Auswahl und Einstellungen werden vorher gespeichert."))},
        confirmButton={TextButton({pending=null;busy=true;error=null;scope.launch{
            val result=onChange?.invoke(logo)?:withContext(Dispatchers.IO){icons.select(logo)}
            result.onSuccess{selected=it}.onFailure{error=tr("Das App-Logo konnte nicht gewechselt werden. Bitte versuche es erneut.")}
            busy=false
        }}){Text(tr("Ändern"))}},dismissButton={TextButton({pending=null}){Text(tr("Verwerfen"))}})}

}

/** Draw the actual adaptive launcher resource, including the current system mask. */
@Composable internal fun NativeAppLogoImage(logo:AppLogo,size:Int,description:String?,modifier:Modifier=Modifier){
    val context=LocalContext.current;val resources=LocalResources.current;val configuration=LocalConfiguration.current
    val pixels=with(LocalDensity.current){size.dp.roundToPx()}
    val bitmap=remember(logo,pixels,resources,configuration,context.theme){
        val drawable=requireNotNull(resources.getDrawable(logo.launcher,context.theme)).mutate()
        Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888).also {b->drawable.setBounds(0,0,pixels,pixels);drawable.draw(Canvas(b))}.asImageBitmap()
    }
    Image(bitmap,description,modifier.size(size.dp))
}
