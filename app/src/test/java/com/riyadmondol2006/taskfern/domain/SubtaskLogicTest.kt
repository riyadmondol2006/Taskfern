package com.riyadmondol2006.taskfern.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskLogicTest {

    // ---- Pasting several lines -----------------------------------------------------------------------------------------

    @Test fun `a single line is still being typed`() {
        assertNull(SubtaskLogic.pastedLines("Buy eggs"))
        assertNull(SubtaskLogic.pastedLines(""))
    }

    @Test fun `each non-blank line becomes a subtask`() {
        assertEquals(listOf("Eggs", "Milk", "Bread"), SubtaskLogic.pastedLines("Eggs\nMilk\n\n   \nBread"))
    }

    @Test fun `windows and old mac line endings split too`() {
        assertEquals(listOf("a", "b", "c"), SubtaskLogic.pastedLines("a\r\nb\rc"))
    }

    @Test fun `pressing enter after a line adds just that line`() {
        assertEquals(listOf("Call the bank"), SubtaskLogic.pastedLines("Call the bank\n"))
    }

    @Test fun `enter on an empty field adds nothing`() {
        assertEquals(emptyList<String>(), SubtaskLogic.pastedLines("\n"))
    }

    @Test fun `list markers copied from notes are dropped`() {
        val pasted = "- Eggs\n* Milk\n• Bread\n1. Butter\n2) Jam\n- [ ] Tea\n[x] Coffee\n☐ Rice\n☑ Beans"
        assertEquals(
            listOf("Eggs", "Milk", "Bread", "Butter", "Jam", "Tea", "Coffee", "Rice", "Beans"),
            SubtaskLogic.pastedLines(pasted),
        )
    }

    @Test fun `text that only looks like a marker is kept`() {
        assertEquals(listOf("-5 degrees", "3.5 kg flour", "2026 plan"), SubtaskLogic.pastedLines("-5 degrees\n3.5 kg flour\n2026 plan"))
    }

    @Test fun `a line that is only a marker is skipped`() {
        assertEquals(listOf("Real step"), SubtaskLogic.pastedLines("- \n[ ]\nReal step"))
    }

    @Test fun `a huge paste is capped`() {
        val text = (1..1000).joinToString("\n") { "Step $it" }
        val lines = SubtaskLogic.pastedLines(text)!!
        assertEquals(SubtaskLogic.MAX_PASTED, lines.size)
        assertEquals("Step 1", lines.first())
    }

    // ---- Titles --------------------------------------------------------------------------------------------------------

    @Test fun `titles are trimmed, single line and capped`() {
        assertEquals("Pack bag", SubtaskLogic.cleanTitle("  Pack bag  "))
        assertEquals("Pack bag", SubtaskLogic.cleanTitle("Pack\nbag"))
        assertNull(SubtaskLogic.cleanTitle("  \n "))
        assertEquals(SubtaskLogic.MAX_TITLE, SubtaskLogic.cleanTitle("x".repeat(5000))!!.length)
    }

    // ---- Progress ------------------------------------------------------------------------------------------------------

    @Test fun `progress counts ticked subtasks`() {
        val p = SubtaskProgress.of(listOf(true, false, true, false, false))
        assertEquals(SubtaskProgress(2, 5), p)
        assertEquals(0.4f, p.fraction, 0.0001f)
        assertFalse(p.allDone)
    }

    @Test fun `all done needs at least one subtask`() {
        assertTrue(SubtaskProgress.of(listOf(true, true)).allDone)
        assertFalse(SubtaskProgress.NONE.allDone)
        assertEquals(0f, SubtaskProgress.NONE.fraction, 0f)
    }

    // ---- Reordering ----------------------------------------------------------------------------------------------------

    @Test fun `moving up and down`() {
        val ids = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(1L, 3L, 2L, 4L), SubtaskLogic.move(ids, 3L, -1))
        assertEquals(listOf(2L, 1L, 3L, 4L), SubtaskLogic.move(ids, 1L, 1))
        assertEquals(listOf(2L, 3L, 4L, 1L), SubtaskLogic.move(ids, 1L, 3))
    }

    @Test fun `moving past either end or an unknown id does nothing`() {
        val ids = listOf(1L, 2L, 3L)
        assertNull(SubtaskLogic.move(ids, 1L, -1))
        assertNull(SubtaskLogic.move(ids, 3L, 1))
        assertNull(SubtaskLogic.move(ids, 9L, 1))
        assertNull(SubtaskLogic.move(ids, 2L, 0))
    }
}
