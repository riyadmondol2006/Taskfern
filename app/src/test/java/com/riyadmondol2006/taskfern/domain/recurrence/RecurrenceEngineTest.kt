package com.riyadmondol2006.taskfern.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class RecurrenceEngineTest {

    private fun d(s: String) = LocalDate.parse(s)

    /** Completes on the due date ("on time") and returns the next due date. */
    private fun next(rule: RecurrenceRule, anchor: String, due: String, count: Int = 0): LocalDate? =
        RecurrenceEngine.nextDueDate(rule, d(anchor), d(due), completedOn = d(due), today = d(due), completedCount = count)

    // ---- Daily ---------------------------------------------------------------------------------------------------------

    @Test fun `daily moves one day`() = assertEquals(d("2026-03-02"), next(RecurrenceRule.daily(), "2026-03-01", "2026-03-01"))

    @Test fun `every 3 days keeps the cadence from the anchor`() {
        val rule = RecurrenceRule.daily(3)
        assertEquals(d("2026-01-04"), next(rule, "2026-01-01", "2026-01-01"))
        assertEquals(d("2026-01-07"), next(rule, "2026-01-01", "2026-01-04"))
    }

    @Test fun `daily crosses month and year boundaries`() {
        assertEquals(d("2027-01-01"), next(RecurrenceRule.daily(), "2026-12-31", "2026-12-31"))
        assertEquals(d("2028-02-29"), next(RecurrenceRule.daily(), "2028-02-28", "2028-02-28"))
    }

    // ---- Weekly --------------------------------------------------------------------------------------------------------

    @Test fun `weekdays skip the weekend`() {
        val rule = RecurrenceRule.weekdays()
        assertEquals(d("2026-11-02"), next(rule, "2026-10-26", "2026-10-30")) // Fri -> Mon
        assertEquals(d("2026-10-28"), next(rule, "2026-10-26", "2026-10-27")) // Tue -> Wed
    }

    @Test fun `selected weekdays`() {
        val rule = RecurrenceRule.weekly(1, MONDAY, THURSDAY)
        assertEquals(d("2026-10-29"), next(rule, "2026-10-26", "2026-10-26")) // Mon -> Thu
        assertEquals(d("2026-11-02"), next(rule, "2026-10-26", "2026-10-29")) // Thu -> Mon
    }

    @Test fun `every 2 weeks on tuesday and friday only counts weeks from the anchor`() {
        val rule = RecurrenceRule.weekly(2, TUESDAY, FRIDAY)
        // Anchor week starts Mon 2026-10-26. Valid weeks: 10-26, 11-09, 11-23 …
        assertEquals(d("2026-10-30"), next(rule, "2026-10-27", "2026-10-27"))
        assertEquals(d("2026-11-10"), next(rule, "2026-10-27", "2026-10-30"))
        assertEquals(d("2026-11-13"), next(rule, "2026-10-27", "2026-11-10"))
    }

    @Test fun `weekly with no days uses the anchor weekday`() {
        val rule = RecurrenceRule(Frequency.WEEKLY)
        assertEquals(d("2026-11-04"), next(rule, "2026-10-28", "2026-10-28")) // Wednesdays
    }

    // ---- Monthly -------------------------------------------------------------------------------------------------------

    @Test fun `monthly on the 31st clamps to short months and comes back`() {
        val rule = RecurrenceRule.monthly()
        assertEquals(d("2026-02-28"), next(rule, "2026-01-31", "2026-01-31"))
        assertEquals(d("2026-03-31"), next(rule, "2026-01-31", "2026-02-28"))
        assertEquals(d("2026-04-30"), next(rule, "2026-01-31", "2026-03-31"))
        assertEquals(d("2026-05-31"), next(rule, "2026-01-31", "2026-04-30"))
    }

    @Test fun `monthly on the 30th in a leap february`() {
        assertEquals(d("2028-02-29"), next(RecurrenceRule.monthly(), "2028-01-30", "2028-01-30"))
    }

    @Test fun `every 3 months`() {
        val rule = RecurrenceRule.monthly(3)
        assertEquals(d("2026-04-15"), next(rule, "2026-01-15", "2026-01-15"))
        assertEquals(d("2027-01-15"), next(rule, "2026-01-15", "2026-10-15"))
    }

    // ---- Yearly --------------------------------------------------------------------------------------------------------

    @Test fun `yearly on feb 29 falls back to feb 28 and returns in leap years`() {
        val rule = RecurrenceRule.yearly()
        assertEquals(d("2029-02-28"), next(rule, "2028-02-29", "2028-02-29"))
        assertEquals(d("2030-02-28"), next(rule, "2028-02-29", "2029-02-28"))
        assertEquals(d("2032-02-29"), next(rule, "2028-02-29", "2031-02-28"))
    }

    @Test fun `every 2 years`() = assertEquals(d("2028-06-01"), next(RecurrenceRule.yearly(2), "2026-06-01", "2026-06-01"))

    // ---- From completion -----------------------------------------------------------------------------------------------

    @Test fun `repeat after completion counts from the completion day`() {
        val rule = RecurrenceRule.daily(3).copy(fromCompletion = true)
        val nextDue = RecurrenceEngine.nextDueDate(
            rule, d("2026-10-01"), currentDue = d("2026-10-01"), completedOn = d("2026-10-05"), today = d("2026-10-05"), completedCount = 0,
        )
        assertEquals(d("2026-10-08"), nextDue)
    }

    @Test fun `monthly after completion from the 31st clamps`() {
        val rule = RecurrenceRule.monthly().copy(fromCompletion = true)
        val nextDue = RecurrenceEngine.nextDueDate(rule, d("2026-01-31"), d("2026-01-31"), d("2026-01-31"), d("2026-01-31"), 0)
        assertEquals(d("2026-02-28"), nextDue)
    }

    // ---- Missed occurrences ------------------------------------------------------------------------------------------

    @Test fun `completing an overdue daily task moves it after today`() {
        val nextDue = RecurrenceEngine.nextDueDate(
            RecurrenceRule.daily(), d("2026-10-01"), currentDue = d("2026-10-20"), completedOn = d("2026-10-29"),
            today = d("2026-10-29"), completedCount = 5,
        )
        assertEquals(d("2026-10-30"), nextDue)
    }

    @Test fun `completing an overdue weekly task keeps its weekday`() {
        val rule = RecurrenceRule.weekly(1, MONDAY)
        val nextDue = RecurrenceEngine.nextDueDate(rule, d("2026-10-05"), d("2026-10-12"), d("2026-10-29"), d("2026-10-29"), 0)
        assertEquals(d("2026-11-02"), nextDue)
    }

    // ---- End conditions ----------------------------------------------------------------------------------------------

    @Test fun `series ends after its end date`() {
        val rule = RecurrenceRule.daily().copy(endDate = d("2026-10-30"))
        assertEquals(d("2026-10-30"), next(rule, "2026-10-28", "2026-10-29"))
        assertNull(next(rule, "2026-10-28", "2026-10-30"))
    }

    @Test fun `series ends after a number of occurrences`() {
        val rule = RecurrenceRule.daily().copy(maxOccurrences = 3)
        assertEquals(d("2026-10-02"), next(rule, "2026-10-01", "2026-10-01", count = 0))
        assertEquals(d("2026-10-03"), next(rule, "2026-10-01", "2026-10-02", count = 1))
        assertNull(next(rule, "2026-10-01", "2026-10-03", count = 2)) // third completion was the last
    }

    @Test fun `skipping does not count toward the occurrence limit`() {
        val rule = RecurrenceRule.daily().copy(maxOccurrences = 1)
        val skipped = RecurrenceEngine.nextDueDate(rule, d("2026-10-01"), d("2026-10-01"), d("2026-10-01"), d("2026-10-01"), 0, countThisOccurrence = false)
        assertEquals(d("2026-10-02"), skipped)
    }

    // ---- Realign, matches, projection --------------------------------------------------------------------------------

    @Test fun `resume after pause picks the next valid day on or after today`() {
        val rule = RecurrenceRule.weekly(1, WEDNESDAY)
        assertEquals(d("2026-11-04"), RecurrenceEngine.realign(rule, d("2026-10-07"), d("2026-10-14"), today = d("2026-10-29")))
        assertEquals(d("2026-10-28"), RecurrenceEngine.realign(rule, d("2026-10-07"), d("2026-10-14"), today = d("2026-10-28")))
    }

    @Test fun `matches recognises occurrences`() {
        val rule = RecurrenceRule.weekdays()
        assertTrue(RecurrenceEngine.matches(rule, d("2026-10-26"), d("2026-10-30")))
        assertFalse(RecurrenceEngine.matches(rule, d("2026-10-26"), d("2026-10-31")))
        assertFalse(RecurrenceEngine.matches(rule, d("2026-10-26"), d("2026-10-20"))) // before the anchor
    }

    @Test fun `occurrences between projects a series onto the calendar`() {
        val list = RecurrenceEngine.occurrencesBetween(RecurrenceRule.weekly(1, MONDAY), d("2026-10-05"), d("2026-10-26"), d("2026-10-01"), d("2026-11-30"))
        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23", "2026-11-30").map(::d), list)
    }

    @Test fun `paused series projects nothing`() {
        val rule = RecurrenceRule.daily().copy(paused = true)
        assertTrue(RecurrenceEngine.occurrencesBetween(rule, d("2026-10-01"), d("2026-10-01"), d("2026-10-01"), d("2026-10-10")).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero interval is rejected`() {
        RecurrenceRule(Frequency.DAILY, interval = 0)
    }

    @Test fun `long sequences never drift`() {
        // Monthly on the 31st for 10 years: every result is the last day of short months or the 31st.
        val rule = RecurrenceRule.monthly()
        var due = d("2026-01-31")
        repeat(120) {
            due = next(rule, "2026-01-31", due.toString())!!
            assertEquals(minOf(31, due.lengthOfMonth()), due.dayOfMonth)
        }
        assertEquals(d("2036-01-31"), due)
    }
}
