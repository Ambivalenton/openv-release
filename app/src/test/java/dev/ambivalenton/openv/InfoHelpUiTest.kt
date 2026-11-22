// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.InfoHelpContent
import dev.ambivalenton.openv.ui.screens.LegalDocumentSheet
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfoHelpUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) = compose.runOnIdle {
        val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 && it.height > 0 }
            ?: compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file = File("../audit/theme-catalog/help-$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }

    @Test fun hubRoutesToTheRightDocumentsWithoutDuplicatedStopCopy() {
        val opened = mutableListOf<String>()
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoHelpContent(opened::add, {}, {}, {}, {})
            }
        } } }
        compose.onNodeWithTag("help-open-guide").assertDoesNotExist()
        compose.onNodeWithText("Anleitungen & Begriffe").performScrollTo().performClick()
        compose.onNodeWithTag("help-open-guide").assertIsDisplayed().performClick()
        compose.onNodeWithTag("help-open-coverage").performScrollTo().performClick()
        compose.onNodeWithText("Rechtliches").performScrollTo().performClick()
        compose.onNodeWithTag("help-open-privacy").performScrollTo().performClick()
        compose.onNodeWithTag("help-open-publisher").performScrollTo().performClick()
        compose.onNodeWithTag("help-open-licenses").performScrollTo().performClick()
        compose.onNodeWithText("Haltestellensuche deutschlandweit").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("help", "coverage", "privacy", "legal", "licenses"), opened) }
        capture("hub-light")
    }

    @Test fun glossaryExpandsItsExplanationWithoutExampleImages() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { GlossarySheet {} } }
        compose.onNodeWithTag("glossary-query-field").performTextInput("Netzplan")
        compose.onNodeWithTag("app-glossary-list").performScrollToNode(hasTestTag("glossary-network"))
        compose.onNodeWithTag("glossary-network").performClick()
        compose.onNodeWithTag("app-glossary-list").performScrollToNode(hasText("Deine Favoriten werden zu Stationen", substring = true))
        compose.onNodeWithText("Deine Favoriten werden zu Stationen", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("help-illustration-network").assertDoesNotExist()
        capture("glossary-network")
        compose.onNodeWithTag("app-glossary").assertExists()
    }

    @Test fun emptySearchHasARecoveryAction() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { GlossarySheet {} } }
        compose.onNodeWithTag("glossary-query-field").performTextInput("xyzzy-nothing")
        compose.onNodeWithText("Kein passender Begriff.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Suche leeren").performClick()
        compose.onNodeWithTag("app-glossary-list").performScrollToNode(hasTestTag("glossary-overview"))
        compose.onNodeWithTag("glossary-overview").assertIsDisplayed()
    }

    @Test fun guideLoadsOfflineAndExpandsInDarkMode() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true, themeMode = ThemeMode.DARK)) { LegalDocumentSheet("help") {} } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1 · Deine Orte, dein Netz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 · Deine Orte, dein Netz").performClick()
        compose.onNodeWithTag("help-document-list").performScrollToNode(hasText("Favorit anlegen:", substring = true))
        compose.onNodeWithText("Favorit anlegen:", substring = true).assertIsDisplayed()
        capture("guide-dark")
    }

    @Test fun largeTypeAndAmoledKeepTheComparisonTableReadable() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true, themeMode = ThemeMode.AMOLED)) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    HelpBlocks(listOf(HelpBlock.Table(listOf(listOf("Aktion", "Ergebnis"), listOf("Anpinnen", "Reisebegleitung"), listOf("Vormerken", "Für später speichern")))))
                } }
            }
        } }
        compose.onNodeWithText("Für später speichern").performScrollTo().assertIsDisplayed()
        capture("large-type-amoled")
    }

    @Test fun englishGlossaryUsesEnglishSearchAndDescriptions() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.UK)
            compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { GlossarySheet {} } }
            compose.onNodeWithTag("glossary-query-field").performTextInput("walking pace")
            compose.onNodeWithTag("app-glossary-list").performScrollToNode(hasTestTag("glossary-walking_pace"))
            compose.onNodeWithTag("glossary-walking_pace").performClick()
            compose.onNodeWithTag("app-glossary-list").performScrollToNode(hasText("Adjusts walking-time checks", substring = true))
            compose.onNodeWithText("Adjusts walking-time checks", substring = true).assertIsDisplayed()
        } finally { Locale.setDefault(previous) }
    }

    @Test fun allDocumentsAndOriginalSoftwareLicencesRemainAvailableOffline() {
        val assets = compose.activity.assets
        for (folder in listOf("legal", "legal/en")) {
            for (name in listOf("help", "coverage", "privacy", "publisher", "notices")) {
                val text = assets.open("$folder/$name.txt").bufferedReader().use { it.readText() }
                assertTrue("$folder/$name", parseHelpDocument(text).isNotEmpty())
                assertFalse(text.contains("{{"))
            }
            val notices = assets.open("$folder/notices.txt").bufferedReader().use { parseHelpDocument(it.readText()) }
            val originals = notices.flatMap { it.blocks }.filterIsInstance<HelpBlock.Literal>().map { it.text }
            for (name in listOf("GPL-3.0", "Apache-2.0", "MapLibre-Native", "MapLibre-Core", "MapLibre-Android")) {
                val original = assets.open("licenses/$name.txt").bufferedReader().use { it.readText() }.trimEnd('\n')
                assertTrue("Missing original $name in $folder", original in originals)
            }
        }
    }
}

