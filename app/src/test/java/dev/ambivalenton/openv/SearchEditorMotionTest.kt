// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.ui.components.SearchEditorMotion
import dev.ambivalenton.openv.ui.components.editorButtonTravel
import dev.ambivalenton.openv.ui.components.editorFold
import dev.ambivalenton.openv.ui.components.rememberSearchEditorMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchEditorMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var editing by mutableStateOf(false)
    private var wanted by mutableStateOf(true)
    private var selection by mutableStateOf<Int?>(1)
    private var quiet by mutableStateOf(false)
    private lateinit var motion: SearchEditorMotion

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent { motion = rememberSearchEditorMotion(editing, selection, wanted, quiet) }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun advanceUntil(condition: () -> Boolean) {
        repeat(120) {
            if (condition()) return
            compose.mainClock.advanceTimeByFrame()
        }
        assertTrue("motion did not reach the expected pose", condition())
    }

    private fun advanceToCrease() {
        editing = true
        compose.mainClock.advanceTimeBy(96)
        assertTrue(motion.row.value in .01f..0.99f)
        assertEquals(0f, motion.paper.value, 0f)
        advanceUntil { motion.paper.value > .2f }
        assertEquals(1f, motion.row.value, 0f)
    }

    @Test fun foldFollowsRowAndPrecedesItsReturn() {
        start()
        advanceToCrease()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(1f, motion.paper.value, 0f)
        editing = false
        advanceUntil { motion.paper.value < .8f }
        assertEquals(1f, motion.row.value, 0f)
        advanceUntil { motion.row.value < .9f }
        assertEquals(0f, motion.paper.value, 0f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0f, motion.row.value, 0f)
    }

    @Test fun deletingDuringFoldHandsThePoseToExitAndNewSelectionStartsFlat() {
        start()
        advanceToCrease()
        wanted = false
        compose.mainClock.advanceTimeBy(32)
        val atDeletion = motion.paper.value
        assertTrue(atDeletion > 0f && atDeletion < 1f)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(atDeletion, motion.paper.value, 0f)
        selection = 2
        wanted = true
        compose.mainClock.advanceTimeBy(64)
        assertEquals(1f, motion.paper.value, 0f)
        assertEquals(0f, editorFold(motion.paper.value), .0001f)
        assertEquals(0f, editorButtonTravel(motion.paper.value), .0001f)
    }

    @Test fun rapidReversalAndReducedMotionSettleBothElements() {
        start()
        advanceToCrease()
        editing = false
        compose.mainClock.advanceTimeBy(64)
        editing = true
        compose.mainClock.advanceTimeBy(64)
        quiet = true
        compose.mainClock.advanceTimeBy(32)
        assertEquals(1f, motion.row.value, 0f)
        assertEquals(1f, motion.paper.value, 0f)
        editing = false
        compose.mainClock.advanceTimeBy(32)
        assertEquals(0f, motion.row.value, 0f)
        assertEquals(0f, motion.paper.value, 0f)
    }

    @Test fun sheetFlattensAndButtonOvershootsItsSeat() {
        for (end in listOf(0f, 1f)) {
            assertEquals(0f, editorFold(end), .0001f)
            assertEquals(0f, editorButtonTravel(end), .0001f)
        }
        assertEquals(1f, editorFold(.42f), .0001f)
        assertTrue(editorButtonTravel(.50f) > editorFold(.50f))
        assertTrue(editorFold(.74f) < 0f)
        assertTrue(editorButtonTravel(.78f) < editorFold(.78f))
    }
}
