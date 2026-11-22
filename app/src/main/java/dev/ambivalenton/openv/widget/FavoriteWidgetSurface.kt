// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.widget
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.*
import androidx.glance.unit.ColorProvider
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.IconStyle
import dev.ambivalenton.openv.ui.theme.contrastContentColor
import kotlin.math.roundToInt
internal fun favoriteShapeResource(shape:IconStyle)=when(shape) {
 IconStyle.ROUNDED->R.drawable.widget_favorite_rounded
 IconStyle.CIRCLE->R.drawable.widget_favorite_circle
 IconStyle.SQUIRCLE->R.drawable.widget_favorite_squircle
 IconStyle.TEARDROP->R.drawable.widget_favorite_teardrop
 IconStyle.COOKIE->R.drawable.widget_favorite_cookie
 IconStyle.CLOVER->R.drawable.widget_favorite_clover
 IconStyle.DIAMOND->R.drawable.widget_favorite_diamond
 IconStyle.STAR->R.drawable.widget_favorite_star
 IconStyle.PILL->R.drawable.widget_favorite_pill
 IconStyle.HEXAGON->R.drawable.widget_favorite_hexagon
 IconStyle.OCTAGON->R.drawable.widget_favorite_octagon
 IconStyle.PENTAGON->R.drawable.widget_favorite_pentagon
 IconStyle.GEM->R.drawable.widget_favorite_gem
 IconStyle.ARCH->R.drawable.widget_favorite_arch
 IconStyle.SLANTED->R.drawable.widget_favorite_slanted
 IconStyle.SUNNY->R.drawable.widget_favorite_sunny
 IconStyle.FLOWER->R.drawable.widget_favorite_flower
 IconStyle.BURST->R.drawable.widget_favorite_burst
 IconStyle.HEART->R.drawable.widget_favorite_heart
 IconStyle.GHOST->R.drawable.widget_favorite_ghost
 IconStyle.PIXEL->R.drawable.widget_favorite_pixel
}
internal fun favoriteSymbolResource(shape:IconStyle,layer:Int):Int {
 require(layer in 0..2)
 return when(shape){
  IconStyle.ROUNDED->when(layer){0->R.drawable.widget_symbol_rounded_shadow;1->R.drawable.widget_symbol_rounded_fill;else->R.drawable.widget_symbol_rounded_contour}
  IconStyle.CIRCLE->when(layer){0->R.drawable.widget_symbol_circle_shadow;1->R.drawable.widget_symbol_circle_fill;else->R.drawable.widget_symbol_circle_contour}
  IconStyle.SQUIRCLE->when(layer){0->R.drawable.widget_symbol_squircle_shadow;1->R.drawable.widget_symbol_squircle_fill;else->R.drawable.widget_symbol_squircle_contour}
  IconStyle.TEARDROP->when(layer){0->R.drawable.widget_symbol_teardrop_shadow;1->R.drawable.widget_symbol_teardrop_fill;else->R.drawable.widget_symbol_teardrop_contour}
  IconStyle.COOKIE->when(layer){0->R.drawable.widget_symbol_cookie_shadow;1->R.drawable.widget_symbol_cookie_fill;else->R.drawable.widget_symbol_cookie_contour}
  IconStyle.CLOVER->when(layer){0->R.drawable.widget_symbol_clover_shadow;1->R.drawable.widget_symbol_clover_fill;else->R.drawable.widget_symbol_clover_contour}
  IconStyle.DIAMOND->when(layer){0->R.drawable.widget_symbol_diamond_shadow;1->R.drawable.widget_symbol_diamond_fill;else->R.drawable.widget_symbol_diamond_contour}
  IconStyle.STAR->when(layer){0->R.drawable.widget_symbol_star_shadow;1->R.drawable.widget_symbol_star_fill;else->R.drawable.widget_symbol_star_contour}
  IconStyle.PILL->when(layer){0->R.drawable.widget_symbol_pill_shadow;1->R.drawable.widget_symbol_pill_fill;else->R.drawable.widget_symbol_pill_contour}
  IconStyle.HEXAGON->when(layer){0->R.drawable.widget_symbol_hexagon_shadow;1->R.drawable.widget_symbol_hexagon_fill;else->R.drawable.widget_symbol_hexagon_contour}
  IconStyle.OCTAGON->when(layer){0->R.drawable.widget_symbol_octagon_shadow;1->R.drawable.widget_symbol_octagon_fill;else->R.drawable.widget_symbol_octagon_contour}
  IconStyle.PENTAGON->when(layer){0->R.drawable.widget_symbol_pentagon_shadow;1->R.drawable.widget_symbol_pentagon_fill;else->R.drawable.widget_symbol_pentagon_contour}
  IconStyle.GEM->when(layer){0->R.drawable.widget_symbol_gem_shadow;1->R.drawable.widget_symbol_gem_fill;else->R.drawable.widget_symbol_gem_contour}
  IconStyle.ARCH->when(layer){0->R.drawable.widget_symbol_arch_shadow;1->R.drawable.widget_symbol_arch_fill;else->R.drawable.widget_symbol_arch_contour}
  IconStyle.SLANTED->when(layer){0->R.drawable.widget_symbol_slanted_shadow;1->R.drawable.widget_symbol_slanted_fill;else->R.drawable.widget_symbol_slanted_contour}
  IconStyle.SUNNY->when(layer){0->R.drawable.widget_symbol_sunny_shadow;1->R.drawable.widget_symbol_sunny_fill;else->R.drawable.widget_symbol_sunny_contour}
  IconStyle.FLOWER->when(layer){0->R.drawable.widget_symbol_flower_shadow;1->R.drawable.widget_symbol_flower_fill;else->R.drawable.widget_symbol_flower_contour}
  IconStyle.BURST->when(layer){0->R.drawable.widget_symbol_burst_shadow;1->R.drawable.widget_symbol_burst_fill;else->R.drawable.widget_symbol_burst_contour}
  IconStyle.HEART->when(layer){0->R.drawable.widget_symbol_heart_shadow;1->R.drawable.widget_symbol_heart_fill;else->R.drawable.widget_symbol_heart_contour}
  IconStyle.GHOST->when(layer){0->R.drawable.widget_symbol_ghost_shadow;1->R.drawable.widget_symbol_ghost_fill;else->R.drawable.widget_symbol_ghost_contour}
  IconStyle.PIXEL->when(layer){0->R.drawable.widget_symbol_pixel_shadow;1->R.drawable.widget_symbol_pixel_fill;else->R.drawable.widget_symbol_pixel_contour}
 }
}
@Composable internal fun providerColors(provider:ColorProvider):Pair<Color,Color> {
 val context=LocalContext.current
 fun resolve(mode:Int):Color {val config=Configuration(context.resources.configuration)
  config.uiMode=(config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
  return provider.getColor(context.createConfigurationContext(config))}
 return resolve(Configuration.UI_MODE_NIGHT_NO) to resolve(Configuration.UI_MODE_NIGHT_YES)
}
@Composable internal fun widgetOpacity(provider:ColorProvider,opacity:Int):ColorProvider {
 if(opacity>=100)return provider
 val (day,night)=providerColors(provider);val factor=opacity.coerceIn(0,100)/100f
 return androidx.glance.color.ColorProvider(day.copy(alpha=day.alpha*factor),night.copy(alpha=night.alpha*factor))
}
@Composable internal fun widgetForeground(provider:ColorProvider):ColorProvider {
 val (day,night)=providerColors(provider)
 return androidx.glance.color.ColorProvider(contrastContentColor(day),contrastContentColor(night))
}
/** Resource-only surfaces; separate image alpha avoids translucent SRC_ATOP white halos. */
@Composable internal fun NativeWidgetImage(resource:Int,color:ColorProvider,modifier:GlanceModifier=GlanceModifier) {
 val context=LocalContext.current;val (day,night)=providerColors(color)
 val view=RemoteViews(context.packageName,R.layout.favorite_widget_image)
 view.setImageViewResource(R.id.favorite_widget_image,resource)
 val dayRgb=day.toArgb() or (0xff shl 24);val nightRgb=night.toArgb() or (0xff shl 24)
 if(Build.VERSION.SDK_INT>=31)view.setColorInt(R.id.favorite_widget_image,"setColorFilter",dayRgb,nightRgb)
 else view.setInt(R.id.favorite_widget_image,"setColorFilter",color.getColor(context).toArgb() or (0xff shl 24))
 view.setInt(R.id.favorite_widget_image,"setImageAlpha",(color.getColor(context).alpha*255).roundToInt())
 AndroidRemoteViews(view,modifier)
}
private fun surfaceResource(radius:Float,w:Float,h:Float,glass:Boolean=false)=when {
 radius<=0->if(glass)R.drawable.widget_surface_square_glass else R.drawable.widget_surface_square
 radius>=minOf(w,h)/2-.1f->if(glass)R.drawable.widget_surface_round_glass else R.drawable.widget_surface_round
 radius<18->if(glass)R.drawable.widget_surface_small_glass else R.drawable.widget_surface_small
 else->if(glass)R.drawable.widget_surface_rounded_glass else R.drawable.widget_surface_rounded
}
@Composable internal fun FavoriteWidgetSurface(width:Float,height:Float,color:ColorProvider,radius:Float,elevation:Float,
 opacity:Int=100,border:ColorProvider?=null,glass:Boolean=false) {
 if(opacity<=0)return
 val resource=surfaceResource(radius,width,height);val cast=if(elevation>0)minOf(3f,height*.05f) else 0f
 Box(GlanceModifier.fillMaxSize()) {
  if(cast>0)NativeWidgetImage(resource,ColorProvider(Color.Black.copy(alpha=.16f*opacity/100)),GlanceModifier.fillMaxSize().padding(top=cast.dp,start=1.dp))
  Box(GlanceModifier.fillMaxSize().padding(bottom=cast.dp,end=if(cast>0)1.dp else 0.dp)) {
   NativeWidgetImage(resource,widgetOpacity(border?:color,opacity),GlanceModifier.fillMaxSize())
   if(border!=null)NativeWidgetImage(resource,widgetOpacity(color,opacity),GlanceModifier.fillMaxSize().padding(1.dp))
   if(glass)NativeWidgetImage(surfaceResource(radius,width,height,true),ColorProvider(Color.White.copy(alpha=opacity/100f)),GlanceModifier.fillMaxSize())
  }
 }
}

/** Preserve hue while making the selected favourite colour a little darker for its contour. */
@Composable internal fun widgetDarker(provider:ColorProvider):ColorProvider {
 val (day,night)=providerColors(provider)
 fun shade(c:Color)=androidx.compose.ui.graphics.lerp(c,Color.Black,.24f).copy(alpha=1f)
 return androidx.glance.color.ColorProvider(shade(day),shade(night))
}
