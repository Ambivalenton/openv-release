// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.isOperatingSystem

@Immutable data class AppVisuals(val theme: AppTheme?=null, val readability: AppReadability=AppReadability(),val customFont:FontFamily?=null,
    /** The design's own bundled typefaces (a chosen app font replaces both). */
    val themeHeadFont:FontFamily?=null,val themeBodyFont:FontFamily?=null) {
    val radiusScale get()=if(theme?.hasSquareCorners==true) 0f else
        (theme?.radiusScale ?: 1f)*readability.normalized().cornerScale
    val fontFamily get()=customFont?:themeBodyFont?:when(theme?.font) {
        ThemeFont.MONO->FontFamily.Monospace;ThemeFont.SERIF->FontFamily.Serif
        ThemeFont.ROUNDED->FontFamily(android.graphics.Typeface.create("sans-serif-rounded",android.graphics.Typeface.NORMAL))
        else->FontFamily.SansSerif
    }
    /** Display and headline face; equals [fontFamily] unless the design pairs two typefaces. */
    val headingFamily get()=customFont?:themeHeadFont?:fontFamily
    val language get()=theme?.language ?: DesignLanguage.MATERIAL
}
val LocalAppVisuals=staticCompositionLocalOf {AppVisuals()}

/** Shapes for Material components. Always convex: platform shadows of Material elevation stay safe. */
fun AppVisuals.corner(size:Dp):CornerBasedShape {
    val r=size*radiusScale
    return when(theme?.shapeStyle) {
        ThemeShape.CUT->CutCornerShape(r*.55f)
        ThemeShape.ASYMMETRIC->RoundedCornerShape(topStart=r,topEnd=r*.25f,bottomEnd=r,bottomStart=r*.25f)
        ThemeShape.BEVEL->CutCornerShape(topStart=r*.2f,topEnd=r*.7f,bottomEnd=r*.2f,bottomStart=r*.7f)
        ThemeShape.SQUIRCLE->SquircleShape(r)
        ThemeShape.PEBBLE->pebbleShape(r)
        ThemeShape.LEAF->RoundedCornerShape(topStart=r*1.5f,topEnd=r*.22f,bottomEnd=r*1.5f,bottomStart=r*.22f)
        // The stepped outline is reserved for surfaces that draw their own shadow (see surfaceShape).
        ThemeShape.PIXEL->CutCornerShape(r*.45f)
        ThemeShape.SHARP->RoundedCornerShape(0.dp)
        else->RoundedCornerShape(r)
    }
}
/** Shape of the app's own surfaces (cards, navigation, ride bars). They never use a platform
 * elevation shadow, so the stepped 8-bit outline is allowed here. */
fun AppVisuals.surfaceShape(size:Dp):Shape =
    if(theme?.shapeStyle==ThemeShape.PIXEL) PixelCornerShape((size*radiusScale).coerceAtLeast(PIXEL_MIN_RADIUS)) else corner(size)
@Composable fun appCorner(size: Dp)=LocalAppVisuals.current.corner(size)
@Composable fun appSurfaceShape(size: Dp)=LocalAppVisuals.current.surfaceShape(size)

fun appThemeScheme(theme: AppTheme,colors:AppThemeColors=theme.defaultColors()): ColorScheme {
    val bg=Color(colors.background);val surface=Color(colors.surface)
    val foreground=Color(readableForeground(colors.text,colors.background))
    fun legible(value: Long,on: Color)=Color(readableForeground(value,on.toArgb().toLong() and 0xffffffffL))
    val primary=legible(colors.accent,bg);val secondary=legible(colors.secondary,bg)
    val base=if(theme.dark) darkColorScheme() else lightColorScheme()
    fun container(accent: Color)=if(theme==AppTheme.AMOLED_BLACK && colors==theme.defaultColors()) Color.Black else lerp(surface,accent,.12f)
    val pc=if(theme==AppTheme.OS_WIN_XP)Color(toneColor(colors.secondary,43.0))else if(theme==AppTheme.BUBBLEGUM && colors==theme.defaultColors()) Color(0xFFF5D9E9) else container(primary)
    val sc=if(theme==AppTheme.BUBBLEGUM && colors==theme.defaultColors()) Color(0xFFD4F3E5) else container(secondary)
    val third=when(theme) {AppTheme.BUBBLEGUM->Color(0xFF67529B);AppTheme.SWISS_METRO->Color(0xFF8B6900);else->secondary}
    return designLanguageScheme(theme,colors,base.copy(primary=primary,onPrimary=contrastContentColor(primary),primaryContainer=pc,onPrimaryContainer=if(theme==AppTheme.OS_WIN_XP)Color.White else legible(colors.accent,pc),
        secondary=secondary,onSecondary=contrastContentColor(secondary),secondaryContainer=sc,onSecondaryContainer=legible(colors.secondary,sc),
        tertiary=third,onTertiary=contrastContentColor(third),tertiaryContainer=container(third),onTertiaryContainer=contrastContentColor(container(third)),
        background=bg,onBackground=foreground,surface=bg,onSurface=foreground,surfaceVariant=surface,onSurfaceVariant=foreground,
        surfaceContainerLowest=bg,surfaceContainerLow=surface.copy(alpha=theme.glassOpacity),surfaceContainer=surface.copy(alpha=theme.glassOpacity),
        surfaceContainerHigh=container(secondary).copy(alpha=(theme.glassOpacity+.02f).coerceAtMost(1f)),
        surfaceContainerHighest=container(primary).copy(alpha=(theme.glassOpacity+.04f).coerceAtMost(1f)),surfaceDim=bg,surfaceBright=surface,
        outline=if(theme in setOf(AppTheme.INDUSTRIAL,AppTheme.COMIC_POP,AppTheme.BRUTALIST)||theme.language.spec.inkBorder&&theme.language!=DesignLanguage.MATERIAL) foreground else lerp(foreground,bg,.35f),
        outlineVariant=if(theme in setOf(AppTheme.INDUSTRIAL,AppTheme.COMIC_POP,AppTheme.BRUTALIST)||theme.language.spec.inkBorder&&theme.language!=DesignLanguage.MATERIAL) foreground else lerp(foreground,bg,.65f),surfaceTint=Color.Transparent,
        inverseSurface=foreground,inverseOnSurface=bg,inversePrimary=secondary))
}

fun appTypography(visuals: AppVisuals): Typography {
    val t=Typography();val family=visuals.fontFamily;val head=visuals.headingFamily
    val scale=visuals.readability.normalized().textScale
    // One weight step heavier, so thin strokes keep their contrast.
    val heavier=visuals.readability.contrast==AppContrast.HIGH
    // A design language brings its own heading weight and tracking.
    val spec=visuals.language.takeIf {it!=DesignLanguage.MATERIAL}?.spec
    fun TextStyle.contrast()=if(!heavier) this else copy(fontWeight=FontWeight(((fontWeight ?: FontWeight.Normal).weight+100).coerceAtMost(900)))
    fun TextStyle.body()=copy(fontFamily=family,fontSize=fontSize*scale,lineHeight=lineHeight*scale).contrast()
    fun TextStyle.label()=body().let {if(spec==null||spec.labelTracking==0f) it else it.copy(letterSpacing=spec.labelTracking.em)}
    // ThemeLanguage: the ten design-language collections bring their own weight and tracking.
    val themeLanguage=visuals.theme.themeLanguageOrStandard
    fun weight(default:FontWeight?)=when(visuals.theme) {
        AppTheme.OS_WIN_8,AppTheme.OS_ANDROID_HOLO->FontWeight.Light
        AppTheme.OS_MAC_CLASSIC,AppTheme.OS_WIN_95,AppTheme.OS_BEOS,AppTheme.OS_GNOME,AppTheme.OS_IOS_GLASS->FontWeight.SemiBold
        // Build 127: the tile designs speak in large, light headings.
        else->if(visuals.theme?.name?.startsWith("TILE_")==true) FontWeight.Light
            else spec?.let {FontWeight(it.headingWeight)} ?: themeLanguage.headingWeight ?: default
    }
    fun TextStyle.heading(display:Boolean)=copy(fontFamily=if(display) head else family,fontSize=fontSize*scale,lineHeight=lineHeight*scale,
        fontWeight=weight(fontWeight),letterSpacing=when {
            spec!=null && display->spec.headingTracking.em
            themeLanguage.headingTracking.isSpecified->themeLanguage.headingTracking
            else->letterSpacing
        }).contrast()
    return t.copy(displayLarge=t.displayLarge.heading(true),displayMedium=t.displayMedium.heading(true),displaySmall=t.displaySmall.heading(true),
        headlineLarge=t.headlineLarge.heading(true),headlineMedium=t.headlineMedium.heading(true),headlineSmall=t.headlineSmall.heading(true),
        titleLarge=t.titleLarge.heading(true),titleMedium=t.titleMedium.heading(false),titleSmall=t.titleSmall.heading(false),
        bodyLarge=t.bodyLarge.body(),bodyMedium=t.bodyMedium.body(),bodySmall=t.bodySmall.body(),
        labelLarge=t.labelLarge.label(),labelMedium=t.labelMedium.label(),labelSmall=t.labelSmall.label())
}

/** Deterministic paint, no image downloads, blur dependency, timers or random texture flicker. */
@Composable fun Modifier.appThemeBackground(): Modifier {
    val theme=LocalAppVisuals.current.theme
    val highContrast=LocalAppVisuals.current.readability.contrast==AppContrast.HIGH
    val quiet=LocalAppSettings.current.reduceMotion
    val bg=MaterialTheme.colorScheme.background
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.secondary
    val textureInk=if(theme?.texture in setOf(ThemeTexture.CIRCUITS,ThemeTexture.ROUTES)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    return drawWithCache {
        val backdrop=buildFullThemeBackdrop(theme?.texture,size)
        onDrawBehind {
        drawRect(bg)
        if(theme?.isOperatingSystem==true){drawSystemWallpaper(theme,bg,primary,secondary);return@onDrawBehind}
        // Design languages get one quiet backdrop at most – never a wallpaper pattern.
        if(theme!=null && theme.language!=DesignLanguage.MATERIAL) {
            if(!quiet && !highContrast) {
                // Build 110: the collections keep their motif under the language's light (stars behind glass, plaid under seams).
                if(theme.texture.name.startsWith("CATALOG_")) drawDecorativeThemeTexture(theme.texture,textureInk,backdrop,bg)
                drawLanguageBackdrop(theme,bg,primary,secondary)
            }
            return@onDrawBehind
        }
        // With "Hoher Textkontrast" no motif or texture runs behind text.
        if(quiet || highContrast) return@onDrawBehind
        if(theme!=null && drawSeasonalThemeArtwork(theme,textureInk))return@onDrawBehind
        // Design languages carry one quiet signature (or none) instead of a wallpaper.
        if(theme!=null && theme.themeLanguage!=ThemeLanguage.STANDARD){drawThemeLanguageBackdrop(theme,bg,primary,secondary);return@onDrawBehind}
        if(theme!=null && theme.texture.ordinal>ThemeTexture.CONCRETE.ordinal) {
            drawDecorativeThemeTexture(theme.texture,textureInk,backdrop,bg)
            return@onDrawBehind
        }
        when(theme?.texture) {
            ThemeTexture.GRID->{
                val step=32.dp.toPx();val ink=textureInk.copy(alpha=.065f)
                var x=0f;while(x<size.width){drawLine(ink,Offset(x,0f),Offset(x,size.height),1f);x+=step}
                var y=0f;while(y<size.height){drawLine(ink,Offset(0f,y),Offset(size.width,y),1f);y+=step}
            }
            ThemeTexture.PAPER,ThemeTexture.CONCRETE->{
                val ink=(if(theme.dark) Color.White else Color.Black).copy(alpha=.035f)
                val step=8.dp.toPx();var y=3f;var row=0
                while(y<size.height){var x=(row%3)*3f;while(x<size.width){drawCircle(ink,.65f,Offset(x,y));x+=step};y+=step;row++}
            }
            ThemeTexture.WOOD->{var y=0f;while(y<size.height){drawLine(Color(0xFFD8B878).copy(alpha=.055f),Offset(0f,y),Offset(size.width,y+12.dp.toPx()),1f);y+=10.dp.toPx()}}
            ThemeTexture.GLASS->{drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.4f),Color.Transparent,Color(0xFF7DA6C4).copy(alpha=.08f))));
                drawLine(Color.White.copy(alpha=.75f),Offset(1f,1f),Offset(size.width-1,1f),1f)}
            else->Unit
        }
    }
    }
}
