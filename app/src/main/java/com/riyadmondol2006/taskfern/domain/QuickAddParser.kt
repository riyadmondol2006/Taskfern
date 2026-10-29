package com.riyadmondol2006.taskfern.domain

import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceEngine
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Local, offline understanding of a few unambiguous phrases typed into Quick Add, e.g.
 * "Call mum tomorrow at 6pm !!" → title "Call mum", due tomorrow 18:00, priority medium.
 *
 * Deliberately small: only phrases with one obvious meaning are recognised. Numeric dates such as "03/04" are left
 * alone because they read differently around the world. The UI shows what was recognised before saving and the user
 * can turn recognition off for that entry, so nothing is ever silently reinterpreted.
 */
object QuickAddParser {

    data class Result(
        val title: String,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val priority: Int? = null,
        val tags: List<String> = emptyList(),
        /** A repeat rule from phrases like "every monday", "daily", "every 3 days". */
        val recurrence: RecurrenceRule? = null,
        /** The fragments that were recognised, in order, for display ("tomorrow", "at 6pm", "!!"). */
        val recognised: List<String> = emptyList(),
    ) {
        val hasMatches: Boolean get() = recognised.isNotEmpty()
    }

    private val weekdayNames: Map<String, DayOfWeek> = buildMap {
        DayOfWeek.entries.forEach { d ->
            val full = d.name.lowercase()
            put(full, d)
            put(full.take(3), d)
        }
        put("tues", DayOfWeek.TUESDAY)
        put("thur", DayOfWeek.THURSDAY)
        put("thurs", DayOfWeek.THURSDAY)
        put("weds", DayOfWeek.WEDNESDAY)
    }

    private val timeRegex = Regex(
        """(?<![\w:])(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)(?!\w)|(?<![\w:])at\s+(\d{1,2}):(\d{2})(?!\w)|(?<![\w:])(\d{1,2}):(\d{2})(?![\w:])""",
        RegexOption.IGNORE_CASE,
    )
    private val inRegex = Regex("""(?<!\w)in\s+(\d{1,3})\s+(day|days|week|weeks|month|months)(?!\w)""", RegexOption.IGNORE_CASE)
    private val nextWeekdayRegex = Regex("""(?<!\w)(next|this|on)?\s*(${weekdayNamesPattern()})(?!\w)""", RegexOption.IGNORE_CASE)
    private val priorityRegex = Regex("""(?<!\S)(!{1,3})(?!\S)""")
    private val tagRegex = Regex("""(?<!\S)#([\p{L}\p{N}_-]{1,32})(?!\S)""")

    private fun weekdayNamesPattern(): String =
        listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "mon", "tues", "tue", "weds", "wed", "thurs", "thur", "thu", "fri", "sat", "sun").joinToString("|")

    fun parse(input: String, today: LocalDate): Result {
        var text = " " + input.trim() + " "
        val recognised = mutableListOf<String>()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var priority: Int? = null
        val tags = mutableListOf<String>()

        fun consume(range: IntRange) {
            text = text.replaceRange(range, " ")
        }

        // Repeats before dates, so "every monday" is a rule rather than next Monday's date.
        var recurrence: RecurrenceRule? = null
        parseRepeat(text)?.let { (rule, range) ->
            recurrence = rule
            recognised += text.substring(range).trim()
            consume(range)
        }

        // Time first so "at 5pm" is not partly eaten by other rules.
        timeRegex.find(text)?.let { m ->
            val parsed = parseTime(m)
            if (parsed != null) {
                time = parsed
                recognised += m.value.trim()
                consume(m.range)
            }
        }

        // Relative words.
        val words = listOf(
            "day after tomorrow" to 2L, "today" to 0L, "tonight" to 0L, "tomorrow" to 1L, "tmrw" to 1L, "tmr" to 1L,
        )
        for ((word, offset) in words) {
            val m = Regex("""(?<!\w)$word(?!\w)""", RegexOption.IGNORE_CASE).find(text) ?: continue
            date = today.plusDays(offset)
            if (word == "tonight" && time == null) time = LocalTime.of(20, 0)
            recognised += m.value.trim()
            consume(m.range)
            break
        }

        if (date == null) {
            Regex("""(?<!\w)next\s+week(?!\w)""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
                date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                recognised += m.value.trim()
                consume(m.range)
            }
        }

        if (date == null) {
            inRegex.find(text)?.let { m ->
                val n = m.groupValues[1].toLong()
                val unit = m.groupValues[2].lowercase()
                date = when {
                    unit.startsWith("day") -> today.plusDays(n)
                    unit.startsWith("week") -> today.plusWeeks(n)
                    else -> today.plusMonths(n)
                }
                recognised += m.value.trim()
                consume(m.range)
            }
        }

        if (date == null) {
            nextWeekdayRegex.find(text)?.let { m ->
                val day = weekdayNames[m.groupValues[2].lowercase()]
                if (day != null) {
                    val qualifier = m.groupValues[1].lowercase()
                    date = if (qualifier == "next") {
                        // "next friday" = the Friday of next week when said early in the week is ambiguous across
                        // cultures; Taskfern uses the plain reading "the next Friday after today" and shows the date.
                        today.with(TemporalAdjusters.next(day))
                    } else {
                        today.with(TemporalAdjusters.nextOrSame(day))
                    }
                    recognised += m.value.trim()
                    consume(m.range)
                }
            }
        }

        priorityRegex.find(text)?.let { m ->
            priority = m.groupValues[1].length
            recognised += m.value.trim()
            consume(m.range)
        }

        tagRegex.findAll(text).toList().asReversed().forEach { m ->
            tags.add(0, m.groupValues[1].lowercase())
            consume(m.range)
        }
        tags.forEach { recognised += "#$it" }

        // A repeating task starts on its first occurrence on or after today (e.g. the coming Monday).
        recurrence?.let { rule ->
            if (date == null) date = RecurrenceEngine.realign(rule, today, null, today) ?: today
        }

        // A time with no date means today. It is not moved to tomorrow when the time has passed; the preview shows
        // "Today 9:00" so the user can see and change it.
        if (time != null && date == null) date = today

        val title = text.replace(Regex("""\s+"""), " ").trim()
        return if (title.isEmpty()) {
            Result(title = input.trim()) // never produce an empty title; fall back to the literal text
        } else {
            Result(title, date, time, priority, tags.distinct(), recurrence, recognised)
        }
    }

    private val unitWords = "(day|days|week|weeks|month|months|year|years)"
    private val dayList = "((?:${weekdayNamesPattern()})(?:\\s*(?:,|and|&)\\s*(?:${weekdayNamesPattern()}))*)"
    private val repeatPatterns: List<Pair<Regex, (MatchResult) -> RecurrenceRule?>> = listOf(
        Regex("""(?<!\w)every\s+(\d{1,3})\s+$unitWords(?!\w)""", RegexOption.IGNORE_CASE) to { m ->
            m.groupValues[1].toInt().takeIf { it in 1..999 }?.let { n -> RecurrenceRule(unitOf(m.groupValues[2]), n) }
        },
        Regex("""(?<!\w)every\s+other\s+$unitWords(?!\w)""", RegexOption.IGNORE_CASE) to { m -> RecurrenceRule(unitOf(m.groupValues[1]), 2) },
        Regex("""(?<!\w)(?:every\s+(?:weekday|work\s*day)s?|on\s+weekdays|weekdays)(?!\w)""", RegexOption.IGNORE_CASE) to { _ -> RecurrenceRule.weekdays() },
        Regex("""(?<!\w)(?:every\s+weekend|on\s+weekends|weekends)(?!\w)""", RegexOption.IGNORE_CASE) to { _ ->
            RecurrenceRule(Frequency.WEEKLY, 1, RecurrenceRule.WEEKEND_MASK)
        },
        Regex("""(?<!\w)every\s+$dayList(?!\w)""", RegexOption.IGNORE_CASE) to { m ->
            val days = Regex(weekdayNamesPattern(), RegexOption.IGNORE_CASE).findAll(m.groupValues[1])
                .mapNotNull { weekdayNames[it.value.lowercase()] }.toList()
            if (days.isEmpty()) null else RecurrenceRule(Frequency.WEEKLY, 1, RecurrenceRule.weekdayMask(*days.toTypedArray()))
        },
        Regex("""(?<!\w)(?:every\s+day|each\s+day|daily)(?!\w)""", RegexOption.IGNORE_CASE) to { _ -> RecurrenceRule.daily() },
        Regex("""(?<!\w)(?:every\s+week|weekly)(?!\w)""", RegexOption.IGNORE_CASE) to { _ -> RecurrenceRule(Frequency.WEEKLY) },
        Regex("""(?<!\w)(?:every\s+month|monthly)(?!\w)""", RegexOption.IGNORE_CASE) to { _ -> RecurrenceRule.monthly() },
        Regex("""(?<!\w)(?:every\s+year|yearly|annually)(?!\w)""", RegexOption.IGNORE_CASE) to { _ -> RecurrenceRule.yearly() },
    )

    private fun unitOf(word: String): Frequency = when {
        word.startsWith("day", ignoreCase = true) -> Frequency.DAILY
        word.startsWith("week", ignoreCase = true) -> Frequency.WEEKLY
        word.startsWith("month", ignoreCase = true) -> Frequency.MONTHLY
        else -> Frequency.YEARLY
    }

    /** The first repeat phrase in [text] and where it is. */
    private fun parseRepeat(text: String): Pair<RecurrenceRule, IntRange>? {
        for ((regex, build) in repeatPatterns) {
            val m = regex.find(text) ?: continue
            val rule = build(m) ?: continue
            return rule to m.range
        }
        return null
    }

    private fun parseTime(m: MatchResult): LocalTime? {
        val g = m.groupValues
        return when {
            g[1].isNotEmpty() -> {
                val hour = g[1].toInt()
                val minute = g[2].ifEmpty { "0" }.toInt()
                if (hour !in 1..12 || minute !in 0..59) return null
                val pm = g[3].lowercase().startsWith("p")
                LocalTime.of((hour % 12) + if (pm) 12 else 0, minute)
            }
            g[4].isNotEmpty() -> {
                val hour = g[4].toInt(); val minute = g[5].toInt()
                if (hour !in 0..23 || minute !in 0..59) null else LocalTime.of(hour, minute)
            }
            g[6].isNotEmpty() -> {
                val hour = g[6].toInt(); val minute = g[7].toInt()
                if (hour !in 0..23 || minute !in 0..59) null else LocalTime.of(hour, minute)
            }
            else -> null
        }
    }
}
