// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.StablePinnedJourneyHeader
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PinnedHeaderLayoutUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    private fun platformAt(scale:Float) {
        RuntimeEnvironment.setFontScale(scale)
        try {
            val platform="Nächster Einstieg: Gleis 12 A–C"
            val model=PinnedHeaderPresentation("Reisebegleiter","Unterwegs","Einsteigen","Dortmund Hbf",
                "09:58","Abfahrt",platform,"+12 Min.","10:45","","",false,false,false)
            compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {
                Column(Modifier.width(240.dp)) {StablePinnedJourneyHeader(model,{},{},{})}
            }}}
            val node=compose.onNodeWithTag("pin-platform").fetchSemanticsNode()
            val layouts=mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout=layouts.single()
            assertEquals(platform,layout.layoutInput.text.text)
            assertFalse("$scale: the platform must never be clipped",layout.hasVisualOverflow)
            for(i in 0 until layout.lineCount)assertFalse(layout.isLineEllipsized(i))
            val time=compose.onNodeWithTag("pin-next-time").fetchSemanticsNode().boundsInRoot
            assertTrue("boarding information has its own row",node.boundsInRoot.top>=time.bottom)
            val image=android.graphics.Bitmap.createBitmap(compose.activity.window.decorView.width,
                compose.activity.window.decorView.height,android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnIdle {compose.activity.window.decorView.draw(android.graphics.Canvas(image))}
            val file=java.io.File("../audit/theme-catalog/pinned-platform-${scale}.png")
            file.parentFile!!.mkdirs();file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        } finally {RuntimeEnvironment.setFontScale(1f)}
    }
    @Test fun narrowHeaderShowsTheWholeBoardingPlatform()=platformAt(1f)
    @Test fun largestSystemFontKeepsThePlatformNumberAndSectionsVisible()=platformAt(2f)
}
