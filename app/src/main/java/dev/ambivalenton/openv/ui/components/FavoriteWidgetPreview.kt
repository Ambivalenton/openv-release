// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import dev.ambivalenton.openv.data.model.StoredState
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.widget.QuickWidgetContent
import dev.ambivalenton.openv.widget.QuickWidgetState
import kotlinx.coroutines.CancellationException
private class FavoritePreviewWidget(val snapshot:StoredState,val font:Float):GlanceAppWidget() {
 override suspend fun provideGlance(context:Context,id:GlanceId) {provideContent {GlanceTheme {QuickWidgetContent(snapshot,font,preview=true)}}}
}
/** Real RemoteViews artwork; a still preview shows the first viewport of a scrollable widget. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun FavoriteWidgetPreview(snapshot:StoredState) {
 val presets=listOf(1 to 1,2 to 2,5 to 1,1 to 5,5 to 5);var selected by rememberSaveable {mutableIntStateOf(1)}
 val (columns,rows)=presets[selected];val size=DpSize((columns*72-8).dp,(rows*72-8).dp)
 val context=LocalContext.current;val density=LocalDensity.current;val input=QuickWidgetState.from(snapshot)
 var failed by remember(input,size,density){mutableStateOf(false)}
 val bitmap by produceState<Bitmap?>(null,input,size,density) {
  try {
   val remote=FavoritePreviewWidget(snapshot,density.fontScale).compose(context,size=size)
   val container=FrameLayout(context);container.addView(remote.apply(context,container))
   val width=with(density){size.width.roundToPx()};val height=with(density){size.height.roundToPx()}
   container.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));container.layout(0,0,width,height)
   value=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also {container.draw(Canvas(it))}
  }catch(e:CancellationException){throw e}catch(_:Exception){failed=true}
 }
 Column(Modifier.fillMaxWidth().testTag("favorite-widget-preview"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {presets.forEachIndexed {i,(w,h)->
   FilterChip(selected==i,{selected=i},label={Text("$w×$h")},modifier=Modifier.testTag("widget-preview-${w}x$h"))}}
  Text(tr("Die Größe änderst du auf dem Startbildschirm."),style=MaterialTheme.typography.bodySmall)
  BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
   val scale=minOf(1f,maxWidth.value/size.width.value)
   Box(Modifier.size(size.width*scale,size.height*scale),contentAlignment=Alignment.Center) {
    bitmap?.let {Image(it.asImageBitmap(),tr("Vorschau"),Modifier.fillMaxSize().testTag("favorite-widget-preview-image"))}?:Text(tr(if(failed)"Vorschau nicht verfügbar." else "Vorschau"))
   }
  }
 }
}
