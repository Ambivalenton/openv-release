// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import dev.ambivalenton.openv.data.model.themeLanguage
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import dev.ambivalenton.openv.rendering.*
import androidx.compose.ui.draw.drawWithCache
import dev.ambivalenton.openv.data.model.reduceMotion

/**
 * The same static motif and colour language on expandable panels and adaptive quote cards.
 * [shape] is the clipping shape of the host card: system rims then follow its corners.
 */
@Composable fun Modifier.appPanelDecoration(shape:androidx.compose.ui.graphics.Shape?=null):Modifier {
    val direction=androidx.compose.ui.platform.LocalLayoutDirection.current
    val theme=LocalAppVisuals.current.theme
    val texture=theme?.texture
    val palette=MaterialTheme.colorScheme.systemSurfacePalette()
    // "Hoher Textkontrast" keeps panels plain behind their text (OS materials stay).
    val quiet=LocalAppSettings.current.reduceMotion || LocalAppVisuals.current.readability.contrast==dev.ambivalenton.openv.data.model.AppContrast.HIGH
    val color=MaterialTheme.colorScheme.secondary
    val primary=MaterialTheme.colorScheme.primary
    val background=MaterialTheme.colorScheme.surfaceContainer
    // Language designs carry their inner material (sheen, stripe, rules) instead of a motif.
    val ink=languageInk()
    // The material follows the host card's own corners (dashed frames and rules never cut across a rounded corner).
    if(ink.active) return languageInnerMaterial(shape ?: androidx.compose.ui.graphics.RectangleShape,ink)
    return drawWithCache {
        val backdrop=buildFullThemeBackdrop(texture,size)
        val outline=shape?.let {host->{inset:Float->hostContour(host,size,inset,direction,this)}}
        onDrawBehind {if(theme?.isOperatingSystem==true)drawIntoCanvas{drawSystemPane(it.nativeCanvas,theme,size.width,size.height,palette,density,outline)}
            // Design languages light their material instead of printing a texture.
            else if(theme!=null && theme.themeLanguage!=dev.ambivalenton.openv.data.model.ThemeLanguage.STANDARD){if(!quiet)drawLanguagePanel(theme,primary,color)}
            else if(!quiet && texture!=null)drawDecorativeThemeTexture(texture,color,backdrop,background)}
    }
}

/** The host shape at a smaller size, moved inwards: corners keep the theme's own geometry. */
internal fun hostContour(shape:androidx.compose.ui.graphics.Shape,size:androidx.compose.ui.geometry.Size,inset:Float,
    direction:androidx.compose.ui.unit.LayoutDirection,density:androidx.compose.ui.unit.Density):android.graphics.Path {
    val inner=androidx.compose.ui.geometry.Size((size.width-2*inset).coerceAtLeast(1f),(size.height-2*inset).coerceAtLeast(1f))
    val path=androidx.compose.ui.graphics.Path().apply {
        when(val o=shape.createOutline(inner,direction,density)) {
            is androidx.compose.ui.graphics.Outline.Generic->addPath(o.path)
            is androidx.compose.ui.graphics.Outline.Rounded->addRoundRect(o.roundRect)
            is androidx.compose.ui.graphics.Outline.Rectangle->addRect(o.rect)
        }
        translate(androidx.compose.ui.geometry.Offset(inset,inset))
    }
    return path.asAndroidPath()
}
