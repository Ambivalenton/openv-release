// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.widget
import android.content.Context
import android.util.TypedValue
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.rendering.FavoriteBadgeArtwork
import dev.ambivalenton.openv.ui.components.resource
import dev.ambivalenton.openv.ui.theme.resolveColorScheme
import java.time.ZonedDateTime
internal data class FavoriteWidgetCell(val title:String,val favorite:Favorite?=null,val manual:Boolean=false,val highlighted:Boolean=false)
internal fun favoriteWidgetCells(state:QuickWidgetState,now:ZonedDateTime):List<FavoriteWidgetCell> =
 visibleFavorites(state.favorites,now,state.presence).map {
  FavoriteWidgetCell(it.label,it,highlighted=it.rule?.matches(now)==true)
 }
private fun widgetLineHeight(context:Context,font:Float,family:String):Float {
 val metrics=context.resources.displayMetrics
 val paint=Paint().apply {textSize=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,font,metrics);typeface=Typeface.create(family,Typeface.BOLD)}
 return kotlin.math.ceil(paint.fontMetrics.bottom-paint.fontMetrics.top)/metrics.density
}
private fun widgetFont(settings:AppSettings)=when(settings.appTheme?.font) {
 ThemeFont.MONO->"monospace";ThemeFont.SERIF->"serif";ThemeFont.ROUNDED->"sans-serif-rounded";else->"sans-serif"
}
internal fun fittedWidgetFont(text:String,width:Float,maxSp:Float,fontScale:Float,family:String,bold:Boolean=false,context:Context?=null):Float {
 val metrics=context?.resources?.displayMetrics
 val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=if(metrics!=null)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,maxSp,metrics) else maxSp*fontScale.coerceIn(.7f,2f);typeface=Typeface.create(family,if(bold)Typeface.BOLD else Typeface.NORMAL)}
 val ratio=((width-2).coerceAtLeast(1f)*(metrics?.density?:1f)/paint.measureText(text).coerceAtLeast(1f)).coerceAtMost(1f)
 return (maxSp*ratio).coerceAtLeast(.5f)
}
@Composable private fun WidgetLabel(text:String,width:Float,maxSp:Float,fontScale:Float,family:String,color:ColorProvider,
 modifier:GlanceModifier=GlanceModifier,bold:Boolean=false,center:Boolean=false) {
 val context=LocalContext.current
 val single=text.replace('\n',' ')
 val font=remember(single,width,maxSp,fontScale,family,bold){fittedWidgetFont(single,width,maxSp,fontScale,family,bold,context)}
 Text(single,modifier,maxLines=1,style=TextStyle(color=color,fontSize=font.sp,fontFamily=FontFamily(family),
  fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,textAlign=if(center)TextAlign.Center else TextAlign.Start))
}
@Composable internal fun QuickFavoriteContent(state:QuickWidgetState,fontScale:Float,now:ZonedDateTime,preview:Boolean=false) {
 val size=LocalSize.current;val style=state.settings.widget.fixedFavoritePresentation();val cells=favoriteWidgetCells(state,now)
 val plan=favoriteWidgetLayout(size.width.value,size.height.value,cells.size,fontScale,style.iconsOnly,cells.count {it.highlighted},style.compactBackground)
 Box(GlanceModifier.fillMaxSize().appWidgetBackground(),contentAlignment=Alignment.TopCenter) {
  Box(GlanceModifier.width(plan.width.dp).height(plan.height.dp)) {
   if(cells.isEmpty())FavoriteWidgetEmpty(state,plan,fontScale)
   else Column(GlanceModifier.fillMaxSize().padding(plan.padding.dp)) {
    if(plan.headerHeight>0){FavoriteWidgetHeader(plan,cells.size,state.settings,fontScale);Spacer(GlanceModifier.height(plan.gap.dp))}
    if(plan.scroll&&!preview)LazyColumn(GlanceModifier.width(plan.innerWidth.dp).height(plan.bodyHeight.dp)) {
     items(plan.rows.size) {i->FavoriteWidgetRowContent(plan.rows[i],cells,plan,state.settings,fontScale,i==plan.rows.lastIndex)}
    } else Column(GlanceModifier.width(plan.innerWidth.dp).height(plan.bodyHeight.dp)) {
     val rows=if(preview&&plan.scroll)plan.rows.take(plan.visibleRows) else plan.rows
     rows.forEachIndexed {i,row->FavoriteWidgetRowContent(row,cells,plan,state.settings,fontScale,i==rows.lastIndex)}
    }
   }
  }
 }
}
@Composable private fun FavoriteBackdrop(plan:FavoriteWidgetLayout,settings:AppSettings) {
 val context=LocalContext.current;val style=settings.widget
 val day=resolveColorScheme(context,settings,false);val night=resolveColorScheme(context,settings,true)
 val color=style.backgroundColor?.let {ColorProvider(Color(it))}?:androidx.glance.color.ColorProvider(day.secondaryContainer,night.secondaryContainer)
 FavoriteWidgetSurface(plan.width,plan.height,color,style.containerShape.radius(plan.width,plan.height),style.designOptions.containerShadowDp,
  style.opacity,style.borderColor?.let {ColorProvider(Color(it))},!settings.quietMode&&style.designOptions.surface==dev.ambivalenton.openv.data.model.WidgetSurface.GLASS&&style.glassDepth>0)
}
@Composable private fun FavoriteWidgetHeader(plan:FavoriteWidgetLayout,count:Int,settings:AppSettings,fontScale:Float) {
 Row(GlanceModifier.width(plan.innerWidth.dp).height(plan.headerHeight.dp).clickable(actionStartActivity(searchIntent(LocalContext.current))),verticalAlignment=Alignment.Vertical.CenterVertically) {
  WidgetLabel(tr("Favoriten"),plan.innerWidth-72,17f,fontScale,widgetFont(settings),GlanceTheme.colors.onSurface,GlanceModifier.defaultWeight(),bold=true)
  Text(count.toString(),style=TextStyle(color=GlanceTheme.colors.onSurface,fontSize=12.sp))
  Box(GlanceModifier.size(44.dp),contentAlignment=Alignment.Center) {Image(ImageProvider(R.drawable.ic_list),tr("Favoriten verwalten"),GlanceModifier.size(24.dp),colorFilter=ColorFilter.tint(GlanceTheme.colors.onSurface))}
 }
}
@Composable private fun FavoriteWidgetRowContent(row:FavoriteWidgetRow,cells:List<FavoriteWidgetCell>,plan:FavoriteWidgetLayout,settings:AppSettings,fontScale:Float,last:Boolean) {
 Column {Row(GlanceModifier.width(plan.innerWidth.dp).height(row.height.dp),horizontalAlignment=Alignment.Horizontal.CenterHorizontally) {
  row.slots.forEachIndexed {i,slot->if(i>0)Spacer(GlanceModifier.width(plan.gap.dp));FavoriteWidgetTile(cells[slot.index],slot,settings,fontScale)}
 };if(!last)Spacer(GlanceModifier.height(plan.gap.dp))}
}
@Composable private fun FavoriteWidgetTile(cell:FavoriteWidgetCell,slot:FavoriteWidgetSlot,settings:AppSettings,fontScale:Float){
 val context=LocalContext.current;val style=settings.widget.fixedFavoritePresentation()
 val colors=GlanceThemeHelper.favoritePair(context,settings.copy(widget=style),cell.favorite?.accentSeed,cell.highlighted)
 val intent=cell.favorite?.let{quickLaunchIntent(context,it.id,style)}?:searchIntent(context)
 Box(GlanceModifier.width(slot.width.dp).height(slot.height.dp).clickable(actionStartActivity(intent)),contentAlignment=Alignment.Center){
  FavoriteWidgetBadge(cell,(minOf(slot.width,slot.height,112f)-4).coerceAtLeast(28f),colors)
 }
}
@Composable private fun FavoriteWidgetBadge(cell:FavoriteWidgetCell,size:Float,colors:WidgetColors){
 val favorite=cell.favorite;val shape=favorite?.shape?:IconStyle.CIRCLE
 val contour=widgetDarker(colors.accent)
 val glyph=favorite?.icon?.resource()?:R.drawable.ic_place
 val glyphSize=size*FavoriteBadgeArtwork.iconFraction(shape)
 Box(GlanceModifier.size(size.dp),contentAlignment=Alignment.Center){
  Image(ImageProvider(favoriteSymbolResource(shape,0)),null,GlanceModifier.fillMaxSize())
  NativeWidgetImage(favoriteSymbolResource(shape,1),colors.accent,GlanceModifier.fillMaxSize())
  NativeWidgetImage(favoriteSymbolResource(shape,2),contour,GlanceModifier.fillMaxSize())
  NativeWidgetImage(glyph,ColorProvider(Color.Black.copy(alpha=.16f)),GlanceModifier.size(glyphSize.dp).padding(top=1.dp))
  Image(ImageProvider(glyph),cell.title,GlanceModifier.size(glyphSize.dp),colorFilter=ColorFilter.tint(colors.onAccent))
 }
}
@Composable private fun FavoriteWidgetEmpty(state:QuickWidgetState,plan:FavoriteWidgetLayout,fontScale:Float) {
 val message=tr(if(state.favorites.isEmpty())"Favorit hinzufügen" else "Favoriten ausgeblendet")
 Box(GlanceModifier.fillMaxSize().padding(6.dp).clickable(actionStartActivity(searchIntent(LocalContext.current))),contentAlignment=Alignment.Center) {
  Column(horizontalAlignment=Alignment.Horizontal.CenterHorizontally,verticalAlignment=Alignment.Vertical.CenterVertically) {
   Image(ImageProvider(if(state.favorites.isEmpty())R.drawable.ic_add else R.drawable.ic_clock),message,GlanceModifier.size(minOf(plan.width*.4f,plan.height*.4f).coerceIn(24f,52f).dp),colorFilter=ColorFilter.tint(GlanceTheme.colors.onSurface))
   
  }
 }
}
@Composable internal fun QuickWidgetFallback(message:String) {
 Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(searchIntent(LocalContext.current))),contentAlignment=Alignment.Center) {
  Image(ImageProvider(R.drawable.ic_place),message,GlanceModifier.size(32.dp),colorFilter=ColorFilter.tint(GlanceTheme.colors.onSurface))
 }
}
