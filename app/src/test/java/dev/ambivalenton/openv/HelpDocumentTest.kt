// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.glossarySearchKey
import dev.ambivalenton.openv.localization.translate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class HelpDocumentTest {
    @Test fun sectionsKeepCalloutsListsAndTableCellsSeparate() {
        val doc = parseHelpDocument("# Guide\r\n\r\nIntro\r\n\r\n## First\r\n\r\n> A note\r\n\r\n- One\r\n- Two\r\n\r\n| Action | Result |\r\n| --- | --- |\r\n| Pin | Guidance |\r\n\r\n---\r\n")
        assertEquals(2, doc.size)
        assertNull(doc[0].title)
        assertEquals(HelpBlock.Paragraph("Intro"), doc[0].blocks.single())
        assertEquals("First", doc[1].title)
        assertEquals(HelpBlock.Callout("A note"), doc[1].blocks[0])
        assertEquals(HelpBlock.Bullets(listOf("One", "Two")), doc[1].blocks[1])
        assertEquals(HelpBlock.Table(listOf(listOf("Action", "Result"), listOf("Pin", "Guidance"))), doc[1].blocks[2])
        assertEquals(HelpBlock.Divider, doc[1].blocks[3])
    }

    @Test fun licenceTextIsNeverInterpretedAsHelpFormatting() {
        val original = "## Not a section\n- Original text\n    Preserve indentation\n```code\nOriginal upstream example\n```\nhttps://example.com/licence\n"
        val doc = parseHelpDocument("# Licences\n\n## GPL\n\n````text\n${original}````\n\n## Next\n\nText\n")
        assertEquals(listOf("GPL", "Next"), doc.map { it.title })
        assertEquals(original.trimEnd('\n'), (doc[0].blocks.single() as HelpBlock.Literal).text)
        assertTrue(parseHelpDocument("\n\n").isEmpty())
    }

    @Test fun glossarySearchIgnoresCaseAccentsAndGermanEszett() {
        assertEquals(glossarySearchKey("Fußwege & Größe"), glossarySearchKey("FUSSWEGE & GROSSE"))
        assertTrue(glossarySearchKey("Netzpläne").contains(glossarySearchKey("netzplane")))
    }

    @Test fun currentGlossaryIsUniqueTranslatedAndContainsTheNewFeatures() {
        assertEquals(appGlossary.size, appGlossary.map { it.id }.distinct().size)
        assertTrue(appGlossary.map { it.id }.containsAll(listOf("network", "bubble", "walking_pace", "backup", "realtime")))
        assertFalse(appGlossary.any { it.id == "ticket_purchase" })
        appGlossary.forEach {
            assertNotEquals(it.descriptionKey, translate(it.descriptionKey, Locale.UK))
            assertNotEquals(it.categoryKey, translate(it.categoryKey, Locale.UK))
        }
    }
}
