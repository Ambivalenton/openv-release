// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeMaterialRenderTest {
    private fun save(bitmap:Bitmap,name:String){
        val file=File("../audit/theme-catalog/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun pixels(bitmap:Bitmap)=IntArray(bitmap.width*bitmap.height).also {
        bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    }
    @Test fun allSixtyThemesPaintDistinctDeterministicArtworkWithReadableText(){
        for(group in catalogThemeGroups){
            val themes=AppTheme.entries.filter {appThemeGroup(it)==group}
            val atlas=Bitmap.createBitmap(960,960,Bitmap.Config.ARGB_8888)
            val out=Canvas(atlas);val hashes=mutableSetOf<Int>()
            for((index,theme) in themes.withIndex()){
                val scheme=appThemeScheme(theme)
                val image=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(image)
                fun paint(){canvas.drawColor(scheme.background.toArgb());drawCatalogThemePattern(canvas,
                    theme.texture,320f,240f,scheme.secondary.toArgb())}
                paint();val first=pixels(image)
                assertTrue("$theme must draw",first.any {it!=scheme.background.toArgb()})
                paint();assertArrayEquals("$theme deterministic",first,pixels(image))
                hashes+=first.contentHashCode()
                val ink=scheme.onBackground.toArgb()
                // Check the painted pixels, including pattern overlaps, rather than just flat swatches.
                assertTrue("$theme contrast",first.filterIndexed {i,_->i%43==0}.all {
                    ColorUtils.calculateContrast(ink,it)>=4.5
                })
                canvas.drawText(theme.title,12f,218f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=ink;textSize=17f})
                out.drawBitmap(image,(index%3)*320f,(index/3)*240f,null)
                image.recycle()
            }
            assertEquals(themes.size,hashes.size)
            save(atlas,group.name.lowercase());atlas.recycle()
        }
    }
    @Test fun motifsAreClippedAndSafeOnCompactCardsAndWideBoards(){
        for(texture in ThemeTexture.entries.filter {it.isCatalogTexture}){
            for((w,h) in listOf(8 to 8,180 to 60,960 to 96,320 to 800)){
                val bitmap=Bitmap.createBitmap(w+8,h+8,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.translate(4f,4f)
                drawCatalogThemePattern(canvas,texture,w.toFloat(),h.toFloat(),Color.BLACK)
                assertEquals("$texture leaks at $w/$h",Color.WHITE,bitmap.getPixel(1,1))
                assertEquals(Color.WHITE,bitmap.getPixel(w+6,h+6))
                bitmap.recycle()
            }
        }
    }
    @Test fun systemHeadersAndPanesHaveReadableInkAndDistinctMaterials(){
        val themes=AppTheme.entries.filter {it.isOperatingSystem}
        val failures=mutableListOf<String>()
        val atlas=Bitmap.createBitmap(960,((themes.size+2)/3)*240,Bitmap.Config.ARGB_8888)
        val out=Canvas(atlas)
        for((index,theme) in themes.withIndex()){
            val scheme=appThemeScheme(theme);val palette=scheme.systemSurfacePalette()
            val image=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.drawColor(palette.background)
            drawSystemHeader(canvas,theme,320f,56f,palette)
            val headerInk=systemHeaderForeground(theme,palette)
            for(y in listOf(16,28,40)){
                val ratio=ColorUtils.calculateContrast(headerInk,image.getPixel(160,y))
                if(ratio<4.5)failures+="$theme header contrast at $y: $ratio"
            }
            val state=canvas.save();canvas.translate(12f,72f);drawSystemPane(canvas,theme,296f,152f,palette);canvas.restoreToCount(state)
            val paneContrast=ColorUtils.calculateContrast(scheme.onSurface.toArgb(),image.getPixel(160,110))
            if(paneContrast<4.5)failures+="$theme pane contrast: $paneContrast"
            canvas.drawText(theme.title,12f,34f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=headerInk;textSize=16f})
            canvas.drawText("Herne → Dortmund",24f,110f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=scheme.onSurface.toArgb();textSize=18f})
            val control=canvas.save();canvas.translate(24f,132f)
            drawSystemControl(canvas,theme,145f,44f,palette.surface or Color.BLACK,true)
            canvas.drawText("Verbindung suchen",8f,27f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=scheme.onSurface.toArgb();textSize=14f})
            canvas.restoreToCount(control)
            out.drawBitmap(image,(index%3)*320f,(index/3)*240f,null);image.recycle()
        }
        save(atlas,"operating-systems");atlas.recycle()
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
