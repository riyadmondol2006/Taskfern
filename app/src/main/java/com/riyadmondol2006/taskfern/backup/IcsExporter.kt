package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.data.db.Priority
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceEngine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Writes open, dated tasks as an iCalendar file (RFC 5545) that calendar apps can import.
 *
 * - A task without a time becomes an all-day event (`DTSTART;VALUE=DATE`, `DTEND` the next day).
 * - A task with a time uses a floating local time (no zone), matching how Taskfern stores times; it has no duration.
 * - Recurring tasks get an `RRULE` only when Taskfern's rule maps to RFC 5545 exactly (see [rrule]); otherwise the
 *   next occurrence is exported as a single event. Paused series are left out.
 * - Text is escaped and lines are folded at 75 octets without splitting UTF-8 characters; lines end with CRLF.
 */
object IcsExporter {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private const val CRLF = "\r\n"

    /** Tasks that are exported: open, live and dated; paused series are skipped. */
    fun exportable(tasks: List<TaskEntity>): List<TaskEntity> = tasks.filter {
        !it.isCompleted && it.deletedAt == null && !it.archived && it.dueDate != null && it.recurrence?.paused != true
    }.sortedWith(compareBy({ it.dueDate }, { it.dueTime }, { it.id }))

    fun export(tasks: List<TaskEntity>, now: Instant, appVersion: String): String {
        val out = StringBuilder()
        fun line(text: String) {
            out.append(fold(text)).append(CRLF)
        }
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//riyadmondol2006//Taskfern ${escape(appVersion)}//EN")
        line("CALSCALE:GREGORIAN")
        line("METHOD:PUBLISH")
        line("X-WR-CALNAME:Taskfern")
        val stamp = UTC.format(now)
        exportable(tasks).forEach { t ->
            val due = t.dueDate ?: return@forEach
            line("BEGIN:VEVENT")
            line("UID:task-${t.id}-${t.createdAt}@taskfern.riyadmondol2006.com")
            line("DTSTAMP:$stamp")
            if (t.dueTime == null) {
                line("DTSTART;VALUE=DATE:${DATE.format(due)}")
                line("DTEND;VALUE=DATE:${DATE.format(due.plusDays(1))}")
            } else {
                line("DTSTART:${LOCAL_DATE_TIME.format(due.atTime(t.dueTime))}")
            }
            line("SUMMARY:${escape(t.title)}")
            if (t.notes.isNotBlank()) line("DESCRIPTION:${escape(t.notes.trim())}")
            if (t.tagList.isNotEmpty()) line("CATEGORIES:${t.tagList.joinToString(",") { escape(it) }}")
            when (t.priority) {
                Priority.HIGH -> line("PRIORITY:1")
                Priority.MEDIUM -> line("PRIORITY:5")
                Priority.LOW -> line("PRIORITY:9")
            }
            rrule(t)?.let { line("RRULE:$it") }
            line("CREATED:${UTC.format(Instant.ofEpochMilli(t.createdAt))}")
            line("LAST-MODIFIED:${UTC.format(Instant.ofEpochMilli(t.updatedAt))}")
            line("TRANSP:TRANSPARENT")
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    /**
     * The RRULE value for a recurring task, or null when there is no exact equivalent:
     * - "repeat after completion" depends on when it is done,
     * - monthly on the 29th/30th (Taskfern uses the last day of shorter months; RFC 5545 would skip them),
     * - yearly on 29 February (same reason),
     * - a due date that is not an occurrence of the rule (the export would shift the series).
     * Monthly on the 31st maps to "last day of the month" (BYMONTHDAY=-1), which is exactly Taskfern's behaviour.
     */
    fun rrule(task: TaskEntity): String? {
        val rule = task.recurrence ?: return null
        val due = task.dueDate ?: return null
        if (rule.fromCompletion || rule.paused) return null
        val anchor = task.seriesAnchor ?: due
        if (!RecurrenceEngine.matches(rule, anchor, due)) return null

        val parts = ArrayList<String>()
        parts += "FREQ=${rule.frequency.name}"
        if (rule.interval > 1) parts += "INTERVAL=${rule.interval}"
        when (rule.frequency) {
            Frequency.DAILY -> {}
            Frequency.WEEKLY -> {
                val days = rule.weekdaySet.ifEmpty { setOf(anchor.dayOfWeek) }
                parts += "BYDAY=${days.sorted().joinToString(",") { it.icsCode() }}"
                parts += "WKST=MO"
            }
            Frequency.MONTHLY -> when (anchor.dayOfMonth) {
                in 1..28 -> {}
                31 -> parts += "BYMONTHDAY=-1"
                else -> return null
            }
            Frequency.YEARLY -> if (anchor.month == Month.FEBRUARY && anchor.dayOfMonth == 29) return null
        }

        val remaining = rule.maxOccurrences?.let { it - task.occurrenceCount }
        if (remaining != null && remaining < 1) return null
        val endDate = rule.endDate
        when {
            remaining != null && endDate != null -> {
                // RFC 5545 allows COUNT or UNTIL, not both: count the occurrences that the earlier limit allows.
                val count = RecurrenceEngine.occurrencesBetween(rule, anchor, due, due, endDate, limit = remaining).size
                if (count < 1) return null
                parts += "COUNT=$count"
            }
            remaining != null -> parts += "COUNT=$remaining"
            endDate != null -> {
                if (endDate.isBefore(due)) return null
                parts += "UNTIL=" + if (task.dueTime == null) DATE.format(endDate) else LOCAL_DATE_TIME.format(endDate.atTime(23, 59, 59))
            }
        }
        return parts.joinToString(";")
    }

    private fun DayOfWeek.icsCode(): String = name.take(2)

    /** Escapes a TEXT value: backslash, semicolon, comma and line breaks; drops other control characters. */
    fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' -> sb.append("\\\\")
                c == ';' -> sb.append("\\;")
                c == ',' -> sb.append("\\,")
                c == '\r' -> {
                    sb.append("\\n")
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                }
                c == '\n' -> sb.append("\\n")
                c == '\t' -> sb.append(c)
                c < ' ' || c == '\u007F' -> {}
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    /**
     * Folds a content line so no physical line is longer than 75 octets (UTF-8), continuing with a single space.
     * Characters (including surrogate pairs) are never split.
     */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val sb = StringBuilder(line.length + line.length / 70 * 3)
        var octets = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val chars = Character.charCount(cp)
            val size = utf8Size(cp)
            if (octets + size > 75) {
                sb.append(CRLF).append(' ')
                octets = 1 // the leading space of the continuation line
            }
            sb.appendCodePoint(cp)
            octets += size
            i += chars
        }
        return sb.toString()
    }

    private fun utf8Size(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    /** Default file name, e.g. "taskfern-tasks-2026-10-29.ics". */
    fun fileName(today: LocalDate): String = "taskfern-tasks-$today.ics"
}
