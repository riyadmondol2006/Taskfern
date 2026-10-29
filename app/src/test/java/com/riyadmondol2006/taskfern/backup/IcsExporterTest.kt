package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.data.db.Priority
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class IcsExporterTest {
    private val now = Instant.parse("2026-10-29T08:15:30Z")

    private fun task(
        id: Long = 1,
        title: String = "Task",
        date: LocalDate? = LocalDate.of(2026, 10, 30),
        time: LocalTime? = null,
        rule: RecurrenceRule? = null,
        anchor: LocalDate? = null,
    ) = TaskEntity(
        id = id, title = title, dueDate = date, dueTime = time, recurrence = rule, seriesAnchor = anchor ?: date.takeIf { rule != null },
        createdAt = 1_700_000_000_000, updatedAt = 1_700_000_100_000,
    )

    private fun unfold(ics: String) = ics.replace("\r\n ", "")

    @Test
    fun `calendar envelope and line endings follow RFC 5545`() {
        val ics = IcsExporter.export(listOf(task()), now, "1.0.0")
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse(ics.replace("\r\n", "").contains("\n"))
        assertTrue(ics.contains("DTSTAMP:20261029T081530Z\r\n"))
        assertTrue(ics.contains("PRODID:"))
    }

    @Test
    fun `a task without a time is an all-day event`() {
        val ics = IcsExporter.export(listOf(task(date = LocalDate.of(2026, 12, 31))), now, "1")
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261231\r\n"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20270101\r\n"))
    }

    @Test
    fun `a task with a time uses a floating local time`() {
        val ics = IcsExporter.export(listOf(task(time = LocalTime.of(14, 5))), now, "1")
        assertTrue(ics.contains("DTSTART:20261030T140500\r\n"))
        assertFalse(ics.contains("DTSTART:20261030T140500Z"))
        assertFalse(ics.contains("TZID"))
    }

    @Test
    fun `only open, live, dated tasks are exported`() {
        val tasks = listOf(
            task(id = 1, title = "Open"),
            task(id = 2, title = "Undated", date = null),
            task(id = 3, title = "Done").copy(isCompleted = true),
            task(id = 4, title = "Trashed").copy(deletedAt = 5),
            task(id = 5, title = "Archived").copy(archived = true),
            task(id = 6, title = "Paused", rule = RecurrenceRule.daily().copy(paused = true)),
        )
        val ics = IcsExporter.export(tasks, now, "1")
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("SUMMARY:Open"))
    }

    @Test
    fun `text is escaped`() {
        assertEquals("a\\, b\\; c\\\\ d\\ne", IcsExporter.escape("a, b; c\\ d\ne"))
        assertEquals("one\\ntwo", IcsExporter.escape("one\r\ntwo"))
        assertEquals("bell", IcsExporter.escape("be\u0007ll"))
        val t = task(title = "Buy milk, eggs; bread").copy(notes = "Line 1\nLine 2", tags = TaskEntity.tagsToColumn(listOf("a,b", "home")))
        val ics = unfold(IcsExporter.export(listOf(t), now, "1"))
        assertTrue(ics.contains("SUMMARY:Buy milk\\, eggs\\; bread\r\n"))
        assertTrue(ics.contains("DESCRIPTION:Line 1\\nLine 2\r\n"))
    }

    @Test
    fun `long lines are folded at 75 octets without splitting characters`() {
        val title = "Ünïcødé 🌿 ".repeat(20)
        val ics = IcsExporter.export(listOf(task(title = title)), now, "1")
        ics.split("\r\n").forEach { line ->
            assertTrue("line too long (${line.toByteArray().size}): $line", line.toByteArray(Charsets.UTF_8).size <= 75)
            assertFalse("split surrogate in: $line", line.any { it.isSurrogate() } && !line.codePoints().allMatch { Character.isValidCodePoint(it) && !(it in 0xD800..0xDFFF) })
        }
        assertTrue(unfold(ics).contains("SUMMARY:" + IcsExporter.escape(title) + "\r\n"))
    }

    @Test
    fun `fold leaves short lines alone and continues with one space`() {
        assertEquals("SUMMARY:short", IcsExporter.fold("SUMMARY:short"))
        val folded = IcsExporter.fold("X".repeat(160))
        val parts = folded.split("\r\n")
        assertEquals(75, parts[0].length)
        assertTrue(parts.drop(1).all { it.startsWith(" ") && it.length <= 75 })
        assertEquals("X".repeat(160), folded.replace("\r\n ", ""))
    }

    @Test
    fun `priority maps to iCalendar priority`() {
        val ics = IcsExporter.export(listOf(task().copy(priority = Priority.HIGH)), now, "1")
        assertTrue(ics.contains("PRIORITY:1\r\n"))
    }

    @Test
    fun `simple rules map to RRULE`() {
        assertEquals("FREQ=DAILY", IcsExporter.rrule(task(rule = RecurrenceRule.daily())))
        assertEquals("FREQ=DAILY;INTERVAL=3", IcsExporter.rrule(task(rule = RecurrenceRule.daily(3))))
        val friday = LocalDate.of(2026, 10, 30)
        assertEquals("FREQ=WEEKLY;BYDAY=FR;WKST=MO", IcsExporter.rrule(task(date = friday, rule = RecurrenceRule(Frequency.WEEKLY))))
        assertEquals(
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;WKST=MO",
            IcsExporter.rrule(task(date = friday, rule = RecurrenceRule.weekly(2, DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))),
        )
        assertEquals("FREQ=MONTHLY", IcsExporter.rrule(task(date = LocalDate.of(2026, 11, 15), rule = RecurrenceRule.monthly())))
        assertEquals("FREQ=YEARLY", IcsExporter.rrule(task(date = LocalDate.of(2026, 11, 15), rule = RecurrenceRule.yearly())))
    }

    @Test
    fun `monthly on the 31st becomes the last day of the month`() {
        val rule = RecurrenceRule.monthly()
        val t = task(date = LocalDate.of(2026, 11, 30), rule = rule, anchor = LocalDate.of(2026, 10, 31))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", IcsExporter.rrule(t))
    }

    @Test
    fun `rules without an exact equivalent export a single event`() {
        assertNull(IcsExporter.rrule(task(date = LocalDate.of(2026, 11, 30), rule = RecurrenceRule.monthly())))
        assertNull(IcsExporter.rrule(task(date = LocalDate.of(2028, 2, 29), rule = RecurrenceRule.yearly())))
        assertNull(IcsExporter.rrule(task(rule = RecurrenceRule.daily().copy(fromCompletion = true))))
        // Due date that is not an occurrence of the series (weekly on Mondays, due on a Friday).
        val monday = LocalDate.of(2026, 10, 26)
        assertNull(IcsExporter.rrule(task(date = LocalDate.of(2026, 10, 30), rule = RecurrenceRule.weekly(1, DayOfWeek.MONDAY), anchor = monday)))
        assertNull(IcsExporter.rrule(task(rule = null)))
    }

    @Test
    fun `end conditions become COUNT or UNTIL, never both`() {
        val start = LocalDate.of(2026, 10, 30)
        val counted = task(date = start, rule = RecurrenceRule.daily().copy(maxOccurrences = 10)).copy(occurrenceCount = 4)
        assertEquals("FREQ=DAILY;COUNT=6", IcsExporter.rrule(counted))

        val until = task(date = start, rule = RecurrenceRule.daily().copy(endDate = LocalDate.of(2026, 11, 30)))
        assertEquals("FREQ=DAILY;UNTIL=20261130", IcsExporter.rrule(until))
        val untilTimed = task(date = start, time = LocalTime.of(8, 0), rule = RecurrenceRule.daily().copy(endDate = LocalDate.of(2026, 11, 30)))
        assertEquals("FREQ=DAILY;UNTIL=20261130T235959", IcsExporter.rrule(untilTimed))

        // Both: the earlier limit wins and is written as a COUNT.
        val both = task(date = start, rule = RecurrenceRule.daily().copy(endDate = LocalDate.of(2026, 11, 2), maxOccurrences = 50))
        assertEquals("FREQ=DAILY;COUNT=4", IcsExporter.rrule(both))
    }

    @Test
    fun `recurring task in the export carries its rule`() {
        val ics = IcsExporter.export(listOf(task(rule = RecurrenceRule.daily(2))), now, "1")
        assertTrue(ics.contains("RRULE:FREQ=DAILY;INTERVAL=2\r\n"))
    }

    @Test
    fun `default file name contains the date`() {
        assertEquals("taskfern-tasks-2026-10-29.ics", IcsExporter.fileName(LocalDate.of(2026, 10, 29)))
    }
}
