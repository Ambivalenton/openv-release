// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.FootprintStyle
import dev.ambivalenton.openv.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FootprintRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun clockGlyphsReceiveFourPhysicalPixelsOfOutlineWithoutChangingTheAccentFill() {
        lateinit var measurer: TextMeasurer
        compose.setContent { measurer = rememberTextMeasurer() }
        compose.runOnIdle {
            val accent = Color(0xFFBE377B)
            for (ink in listOf(Color.White, Color.Black)) {
                val clock = FootprintClockText(measurer.measure("8888", TextStyle(fontSize = 60.sp)), Offset(8f, 8f), ink)
                var boundary = 40
                fun render(footprint: Boolean): android.graphics.Bitmap {
                    val image = ImageBitmap(180, 100)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(180f, 100f)) {
                        drawRect(Color.Gray)
                        drawText(clock.layout, color = ink, topLeft = clock.topLeft)
                        if (footprint) drawFootprintOverClocks(Path().apply {
                            addRect(Rect(boundary.toFloat(), 0f, boundary + 20f, 100f))
                        }, accent, 1f, listOf(clock))
                    }
                    return image.asAndroidBitmap()
                }
                val before = render(false)
                // Select a real solid glyph column, independent of font metrics and digit spacing.
                val column = (14..100).maxBy { x -> (0 until 100).count { y -> (0..3).all { dx -> before.getPixel(x-dx, y) == ink.toArgb() } } }
                val solidGlyphs = (0 until 100).count { y -> (0..3).all { dx -> before.getPixel(column-dx, y) == ink.toArgb() } }
                assertTrue("the control image contains real opaque clock glyphs", solidGlyphs > 5)
                boundary = column + 1
                val after = render(true)
                val evidence = java.io.File("../audit/theme-catalog/footprint-contour-${if(ink==Color.White) "white" else "black"}.png")
                evidence.parentFile!!.mkdirs()
                evidence.outputStream().use { after.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                val opposite = footprintOutlineColor(ink).toArgb()
                var outlined = 0
                for (y in 0 until 100) {
                    for (x in boundary+2..boundary+18) assertEquals("the whole sole keeps its accent", accent.toArgb(), after.getPixel(x, y))
                    for (x in 0..boundary-5) assertEquals("no broad halo or rectangle", before.getPixel(x, y), after.getPixel(x, y))
                    if ((0..3).all { dx -> before.getPixel(column-dx, y) == ink.toArgb() }) {
                        for (dx in 0..3) assertEquals("four visible outside pixels", opposite, after.getPixel(column-dx, y))
                        outlined++
                    }
                }
                assertTrue("four-pixel contour on $ink at $column: $outlined of $solidGlyphs solid glyph pixels", outlined > 0)
                before.recycle(); after.recycle()
            }
        }
    }


    @Test fun theTranslatedClockBorderReceivesTheSameContourUsingItsOwnContrast() {
        lateinit var measurer: TextMeasurer
        compose.setContent { measurer = rememberTextMeasurer() }
        compose.runOnIdle {
            val accent = Color(0xFFBE377B)
            for (borderInk in listOf(Color.White, Color.Black)) {
                val frame = FootprintClockFrame(RoundedCornerShape(8.dp), Size(158f, 78f), Offset(11f, 11f),
                    2f, borderInk, Rect(10f, 10f, 170f, 90f))
                val clock = FootprintClockText(measurer.measure("12:34", TextStyle(fontSize=16.sp)),
                    Offset(30f, 30f), if(borderInk==Color.White) Color.Black else Color.White, frame)
                    .translated(Offset(20f, 30f)).translated(Offset(-13f, -21f))
                val positioned = clock.frame!!
                assertEquals(Rect(17f, 19f, 177f, 99f), positioned.clipBounds)
                val image = ImageBitmap(190, 110)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(190f, 110f)) {
                    drawRect(Color.Gray)
                    translate(positioned.topLeft.x,positioned.topLeft.y) {
                        drawOutline(positioned.shape.createOutline(positioned.size,layoutDirection,this),borderInk,style=Stroke(2f))
                    }
                    drawText(clock.layout,color=clock.color,topLeft=clock.topLeft)
                    drawFootprintOverClocks(Path().apply {addRect(Rect(50f,0f,70f,110f))},accent,1f,listOf(clock))
                }
                val bitmap=image.asAndroidBitmap()
                for(x in 46..49) assertEquals("outline crosses the actual chip rim",footprintOutlineColor(borderInk).toArgb(),bitmap.getPixel(x,20))
                assertEquals("no fifth outside pixel",borderInk.toArgb(),bitmap.getPixel(45,20))
                for(x in 52..68) assertEquals("border never recolours the sole",accent.toArgb(),bitmap.getPixel(x,20))
                val file=java.io.File("../audit/theme-catalog/footprint-border-${if(borderInk==Color.White) "white" else "black"}.png")
                file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
    }

    @Test fun monochromeUsesGreyAndSmallerSharedSizesAllowMoreStraightSteps() {
        assertEquals(Color.Gray, footprintAccentColor(Color.Magenta, true))
        assertEquals(Color.Gray, footprintAccentColor(Color.Black, false))
        assertEquals(Color.Gray, footprintAccentColor(Color.White, false))
        assertEquals(Color.Magenta, footprintAccentColor(Color.Magenta, false))
        val small = walkingGaitSteps(52f, 180f, 1L, .7f, true, FootprintStyle.SHOE)
        val large = walkingGaitSteps(52f, 180f, 1L, 2f, true, FootprintStyle.SHOE)
        assertTrue(small.size > large.size)
        assertTrue(small.all { kotlin.math.abs(it.geometry.angle) < .04f })
    }
}
