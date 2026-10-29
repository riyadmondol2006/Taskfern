package com.riyadmondol2006.taskfern.abuse

import com.riyadmondol2006.taskfern.backup.BackupCodec
import com.riyadmondol2006.taskfern.backup.BackupReadResult
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import com.riyadmondol2006.taskfern.domain.GroceryCategories
import com.riyadmondol2006.taskfern.domain.QuickAddParser
import com.riyadmondol2006.taskfern.domain.ReminderCalculator
import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceEngine
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.shopping.ShoppingLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * What real users (and accidents) throw at the app: empty and enormous text, emoji, other scripts, pasted garbage,
 * impossible numbers, corrupt files. Nothing here may crash, hang, or produce an empty title.
 */
class HostileInputTest {
    private val today = LocalDate.of(2026, 10, 29)

    // ---- Quick Add parser ------------------------------------------------------------------------------------------

    private val nastyTitles = listOf(
        "", " ", "\n\n\t", "!!!", "#", "#tag", "##", "at", "at 5", "at :", "12:", ":30", "25:99", "13pm", "0am", "99:99pm",
        "in days", "in 0 days", "in 999 days", "in 99999999999 days", "next", "next next week", "tomorrow tomorrow",
        "today tomorrow monday", "!!!!", "! ! !", "#a #b #c #d #e", "🌿🌿🌿", "اتصل بأمي غدا", "明日 母に電話", "Ремонт завтра",
        "‮evil rtl override", "\u0000null\u0000byte", "a".repeat(10_000), "tomorrow ".repeat(500),
        "<script>alert(1)</script>", "'; DROP TABLE tasks; --", "%_\\%_", "\uD83D", "at 5pm at 6pm at 7pm",
        "Mondays", "today's notes", "SUNDAY!!!", "10:00 10:00", "in 3 weeks in 2 days",
    )

    @Test fun `parser never crashes and never returns an empty title`() {
        nastyTitles.forEach { input ->
            val r = QuickAddParser.parse(input, today)
            if (input.isNotBlank()) assertTrue("empty title for <${input.take(40)}>", r.title.isNotBlank())
            r.time?.let { assertTrue(it in LocalTime.MIN..LocalTime.MAX) }
            r.priority?.let { assertTrue(it in 1..3) }
        }
    }

    @Test fun `parser survives random garbage quickly`() {
        val rnd = Random(42)
        val alphabet = "abc tomorrow monday at 5pm !#:0123456789 in days weeks 🌿\n\t "
        val start = System.nanoTime()
        repeat(5_000) {
            val s = buildString { repeat(rnd.nextInt(0, 120)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            val r = QuickAddParser.parse(s, today)
            if (s.isNotBlank()) assertTrue(r.title.isNotBlank())
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("5000 random inputs took $ms ms", ms < 5_000)
    }

    @Test fun `parser on a huge paste stays fast`() {
        val huge = "Buy milk tomorrow at 5pm #shop !! ".repeat(2_000)
        val start = System.nanoTime()
        QuickAddParser.parse(huge, today)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
    }

    @Test fun `absurd relative offsets do not overflow`() {
        // "in 999 months" is within LocalDate range; huge numbers are not matched by the 1-3 digit rule.
        assertEquals(today.plusMonths(999), QuickAddParser.parse("x in 999 months", today).date)
        QuickAddParser.parse("x in 99999999999 days", today) // must not throw
    }

    // ---- Recurrence -------------------------------------------------------------------------------------------------

    @Test fun `every rule terminates even far from its anchor`() {
        val rules = listOf(
            RecurrenceRule.daily(), RecurrenceRule.daily(999), RecurrenceRule.weekdays(),
            RecurrenceRule(Frequency.WEEKLY, 999, 0x7F), RecurrenceRule(Frequency.WEEKLY, 3, 0b1000000),
            RecurrenceRule.monthly(), RecurrenceRule.monthly(999), RecurrenceRule.yearly(), RecurrenceRule.yearly(999),
            RecurrenceRule.daily().copy(fromCompletion = true), RecurrenceRule.monthly().copy(endDate = LocalDate.of(2000, 1, 1)),
        )
        val anchors = listOf(LocalDate.of(1970, 1, 31), LocalDate.of(2000, 2, 29), today, LocalDate.of(2099, 12, 31))
        rules.forEach { rule ->
            anchors.forEach { anchor ->
                val start = System.nanoTime()
                val next = RecurrenceEngine.nextDueDate(rule, anchor, anchor, today, today, 0)
                val ms = (System.nanoTime() - start) / 1_000_000
                assertTrue("$rule from $anchor took $ms ms", ms < 1_000)
                if (next != null && rule.endDate == null) assertTrue("$rule from $anchor gave $next", next.isAfter(today) || rule.fromCompletion)
            }
        }
    }

    @Test fun `end date before the start ends the series immediately`() {
        val rule = RecurrenceRule.daily().copy(endDate = today.minusYears(1))
        assertEquals(null, RecurrenceEngine.nextDueDate(rule, today, today, today, today, 0))
    }

    @Test fun `completing a series overdue for decades catches up once`() {
        val next = RecurrenceEngine.nextDueDate(RecurrenceRule.daily(), LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 1), today, today, 0)
        assertEquals(today.plusDays(1), next)
    }

    @Test fun `projection is bounded`() {
        val list = RecurrenceEngine.occurrencesBetween(RecurrenceRule.daily(), today, today, today, today.plusYears(50), limit = 366)
        assertEquals(366, list.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative interval is rejected`() {
        RecurrenceRule(Frequency.DAILY, -1)
    }

    @Test fun `reminders far in the past or future are handled`() {
        val zone = ZoneId.of("UTC")
        assertEquals(null, ReminderCalculator.nextReminder(LocalDate.of(1900, 1, 1), null, listOf(0), null, LocalTime.NOON, Instant.now(), zone))
        ReminderCalculator.nextReminder(LocalDate.of(9999, 12, 31), LocalTime.MAX, listOf(0, 60 * 24 * 365), null, LocalTime.NOON, Instant.now(), zone)
    }

    // ---- Shopping ---------------------------------------------------------------------------------------------------

    @Test fun `shopping input never loses the item name`() {
        val inputs = listOf(
            "", " ", "2", "2 kg", "kg", "0 eggs", "-3 eggs", "1e9 rice", "999999999999999999999 rice", "½ cup sugar",
            "3,5 kg flour", "eggs x", "x3", "🍎 x 4", "milk 2 2 2", "a".repeat(5_000), "2 kg 3 kg rice",
        )
        inputs.forEach { input ->
            val parsed = ShoppingRepository.parseInput(input)
            if (input.isNotBlank()) assertTrue("lost name for <${input.take(30)}>", parsed.name.isNotBlank())
            GroceryCategories.guess(parsed.name)
        }
    }

    @Test fun `price maths never overflows or goes negative from odd quantities`() {
        assertEquals(199L, ShoppingLogic.lineTotalCents(199, "abc"))
        assertEquals(199L, ShoppingLogic.lineTotalCents(199, ""))
        val huge = ShoppingLogic.lineTotalCents(Long.MAX_VALUE / 4, "2")
        assertTrue(huge == null || huge >= 0)
        val negative = ShoppingLogic.lineTotalCents(199, "-3")
        assertTrue(negative == null || negative >= 0)
    }

    @Test fun `money formatting handles negatives and big values`() {
        assertEquals("-0.05", Formatters.money(-5, ""))
        assertFalse(Formatters.money(Long.MAX_VALUE, "€").isEmpty())
    }

    // ---- Backup files -------------------------------------------------------------------------------------------------

    @Test fun `corrupt and hostile backup files are rejected without crashing`() {
        val bad = listOf(
            "", " ", "null", "[]", "{}", "{", "\u0000\u0001\u0002", "not json at all", "{\"format\":\"something-else\",\"version\":1}",
            "{\"format\":\"taskfern-backup\",\"version\":999,\"exportedAt\":0}",
            "{\"format\":\"taskfern-backup\",\"version\":1}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":[{\"id\":1,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0,\"listId\":77}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":[{\"id\":1,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0},{\"id\":1,\"title\":\"dup\",\"createdAt\":0,\"updatedAt\":0}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":[{\"id\":1,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0,\"dueDate\":\"2026-02-30\"}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":[{\"id\":1,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0,\"recurrence\":{\"frequency\":\"HOURLY\"}}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":[{\"id\":1,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0,\"recurrence\":{\"frequency\":\"DAILY\",\"interval\":0}}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":[{\"id\":-5,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":[{\"id\":1,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0,\"priority\":42}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"subtasks\":[{\"id\":1,\"taskId\":9,\"title\":\"orphan\"}]}",
            "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"tasks\":\"not a list\"}",
            "[".repeat(10_000) + "]".repeat(10_000),
        )
        bad.forEach { text ->
            val result = BackupCodec.decode(text)
            assertTrue("accepted a bad file: ${text.take(80)}", result is BackupReadResult.Failure)
        }
    }

    @Test fun `unknown extra fields from a future build are tolerated`() {
        val text = "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0,\"futureField\":true," +
            "\"tasks\":[{\"id\":1,\"title\":\"x\",\"createdAt\":0,\"updatedAt\":0,\"colour\":\"teal\"}]}"
        assertTrue(BackupCodec.decode(text) is BackupReadResult.Success)
    }

    @Test fun `unicode survives a backup round trip`() {
        val text = "{\"format\":\"taskfern-backup\",\"version\":1,\"exportedAt\":0," +
            "\"tasks\":[{\"id\":1,\"title\":\"🌿 اتصل 明日 \\\"quoted\\\" \\\\ back\",\"createdAt\":0,\"updatedAt\":0}]}"
        val decoded = BackupCodec.decode(text) as BackupReadResult.Success
        val again = BackupCodec.decode(BackupCodec.encode(decoded.file)) as BackupReadResult.Success
        assertEquals(decoded.file.tasks.single().title, again.file.tasks.single().title)
    }
}
