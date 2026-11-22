// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import dev.ambivalenton.openv.data.model.themeLanguage
import androidx.compose.material3.*
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import dev.ambivalenton.openv.data.model.AppTheme
import dev.ambivalenton.openv.rendering.*

fun ColorScheme.systemSurfacePalette()=SystemSurfacePalette(background.toArgb(),surfaceContainer.toArgb(),primary.toArgb(),secondary.toArgb(),onSurface.toArgb())
@Composable fun systemHeaderInk():Color {
    val theme=LocalAppVisuals.current.theme?:return MaterialTheme.colorScheme.onSurface
    return if(theme.isOperatingSystem)Color(systemHeaderForeground(theme,MaterialTheme.colorScheme.systemSurfacePalette()))
        else languageInk().headerContent(MaterialTheme.colorScheme.onSurface)
}
@Composable fun Modifier.appSystemHeader():Modifier {
    val theme=LocalAppVisuals.current.theme
    val scheme=MaterialTheme.colorScheme
    val palette=scheme.systemSurfacePalette()
    // The ten ThemeLanguage collections close the top bar with their own rule (ink, hairline or none).
    if(theme!=null && theme.themeLanguage!=dev.ambivalenton.openv.data.model.ThemeLanguage.STANDARD)
        return drawWithCache{onDrawBehind{drawLanguageHeaderRule(theme,scheme)}}
    if(theme!=null && !theme.isOperatingSystem && theme.language!=dev.ambivalenton.openv.data.model.DesignLanguage.MATERIAL) {
        // Rule, double rule, colour block, gradient line or glass band of the design language,
        // starting below the status bar; the flag band of the rainbow designs sits on the lower edge.
        val ink=languageInk()
        val top=androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(androidx.compose.ui.platform.LocalDensity.current).toFloat()
        return drawBehind {drawLanguageHeader(theme,ink,top.coerceAtMost(size.height))}
    }
    return if(theme?.isOperatingSystem!=true)this else drawWithCache{onDrawBehind{
        drawIntoCanvas{drawSystemHeader(it.nativeCanvas,theme,size.width,size.height,palette,density)}
    }}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun appTopBarColors():TopAppBarColors {
    val theme=LocalAppVisuals.current.theme
    val os=theme?.isOperatingSystem==true || theme!=null && theme.language!=dev.ambivalenton.openv.data.model.DesignLanguage.MATERIAL
    val ink=systemHeaderInk()
    return TopAppBarDefaults.topAppBarColors(containerColor=if(os)Color.Transparent else MaterialTheme.colorScheme.background,
        scrolledContainerColor=if(os)Color.Transparent else MaterialTheme.colorScheme.background,
        navigationIconContentColor=ink,titleContentColor=ink,actionIconContentColor=ink)
}

/** Soft, original backdrops supply colour through clear materials without remote imagery. */
internal fun DrawScope.drawSystemWallpaper(t:AppTheme,bg:Color,accent:Color,secondary:Color){
    when(t){
        AppTheme.OS_WIN_XP->{
            drawRect(bg)
            val foot=size.height*.84f
            drawPath(Path().apply{moveTo(0f,size.height);lineTo(0f,foot);cubicTo(size.width*.3f,foot-size.height*.07f,size.width*.6f,foot+size.height*.07f,size.width,foot-size.height*.03f);lineTo(size.width,size.height);close()},Brush.verticalGradient(listOf(secondary.copy(alpha=.05f),secondary.copy(alpha=.16f)),startY=foot,endY=size.height))
        }
        AppTheme.OS_IOS_GLASS->{
            drawRect(Brush.linearGradient(listOf(bg,lerp(bg,accent,.08f),lerp(bg,secondary,.09f))))
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=.14f),Color.Transparent),center=Offset(size.width*.12f,size.height*.2f),radius=size.width*.95f),radius=size.width*.95f,center=Offset(size.width*.12f,size.height*.2f))
            drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha=.16f),Color.Transparent),center=Offset(size.width*.95f,size.height*.76f),radius=size.width*1.1f),radius=size.width*1.1f,center=Offset(size.width*.95f,size.height*.76f))
        }
        AppTheme.OS_WIN_7->{
            drawRect(Brush.linearGradient(listOf(bg,lerp(bg,accent,.08f),bg)))
            repeat(3){i->val x=size.width*(-.5f+i*.45f)
                drawPath(Path().apply{moveTo(x,0f);lineTo(x+size.width*.2f,0f);lineTo(x+size.width*1.2f,size.height);lineTo(x+size.width,size.height);close()},Color.White.copy(alpha=.16f))}
        }
        AppTheme.OS_IOS_4->{drawRect(bg);val step=4*density;var x=0f;while(x<size.width){drawLine(Color.Black.copy(alpha=.025f),Offset(x,0f),Offset(x,size.height),density);x+=step}}
        AppTheme.OS_MAC_AQUA->{drawRect(bg);val step=4*density;var y=0f;while(y<size.height){drawLine(accent.copy(alpha=.035f),Offset(0f,y),Offset(size.width,y),density);y+=step}}
        AppTheme.OS_MAC_LEOPARD->{
            drawRect(bg)
            drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha=.16f),Color.Transparent),Offset(size.width*.8f,size.height*.2f),size.width),size.width,Offset(size.width*.8f,size.height*.2f))
            // No scattered star dots; at 0.6 dp they read as dead pixels on real screens.
        }
        AppTheme.OS_WIN_95->{drawRect(bg);drawRect(Brush.verticalGradient(listOf(secondary.copy(alpha=.055f),Color.Transparent)))}
        AppTheme.OS_MAC_CLASSIC->{
            drawRect(bg);val step=6*density
            val rows=(size.height/step).toInt().coerceAtMost(200);val cols=(size.width/step).toInt().coerceAtMost(100)
            repeat(rows){row->repeat(cols){col->drawCircle(Color.Black.copy(alpha=.045f),.5f*density,Offset(col*step+(row%2)*step/2,row*step))}}
        }
        AppTheme.OS_AMIGA,AppTheme.OS_ATARI,AppTheme.OS_NEXTSTEP,AppTheme.OS_OS2,AppTheme.OS_CDE->{drawRect(bg)}
        AppTheme.OS_GNOME->{drawRect(bg)}
        AppTheme.OS_UBUNTU->{
            drawRect(bg)
            drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha=.055f),Color.Transparent),Offset(size.width,size.height*.8f),size.width),size.width,Offset(size.width,size.height*.8f))
        }
        AppTheme.OS_WIN_8,AppTheme.OS_ANDROID_HOLO,AppTheme.OS_BEOS->{drawRect(bg)}
        else->drawRect(Brush.verticalGradient(listOf(bg,lerp(bg,secondary,.035f))))
    }
}

