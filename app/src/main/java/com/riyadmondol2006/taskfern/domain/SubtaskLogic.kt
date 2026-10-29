package com.riyadmondol2006.taskfern.domain

/** How far a task's subtasks have got, e.g. "2 of 5". */
data class SubtaskProgress(val done: Int, val total: Int) {
    val allDone: Boolean get() = total > 0 && done >= total
    val fraction: Float get() = if (total == 0) 0f else done.coerceIn(0, total).toFloat() / total

    companion object {
        val NONE = SubtaskProgress(0, 0)

        fun of(doneFlags: Collection<Boolean>) = SubtaskProgress(doneFlags.count { it }, doneFlags.size)
    }
}

/** Pure text and ordering rules for subtasks (a task's checklist steps). */
object SubtaskLogic {
    /** Longest subtask title kept; longer text is cut. */
    const val MAX_TITLE = 500

    /** Most subtasks created from one paste, so pasting a whole document cannot freeze the editor. */
    const val MAX_PASTED = 200

    private val lineBreak = Regex("\r\n|[\r\n\u2028\u2029]")

    /**
     * A list marker copied along with the text: "- ", "* ", "• ", "☐ ", "[ ] ", "[x] ", "1. ", "2) " and combinations
     * such as "- [ ] ". A marker needs the space after it, so "-5 degrees" or "3.5 kg" are left alone.
     */
    private val marker = Regex("""^(?:[-*+•◦▪‣–—☐☑☒✓✔](?:\s+|$))?(?:\[[ xX✓✔]?](?:\s+|$))?(?:\d{1,3}[.)](?:\s+|$))?""")

    /** A title as stored: one line, trimmed, capped; null when nothing is left. */
    fun cleanTitle(text: String): String? =
        text.replace(lineBreak, " ").trim().take(MAX_TITLE).trim().takeIf { it.isNotEmpty() }

    /**
     * What typing or pasting into the "Add subtask" field produced, when it contains a line break: one title per
     * non-blank line, without list markers. Null when [text] is still a single line (keep editing it).
     */
    fun pastedLines(text: String): List<String>? {
        if (!lineBreak.containsMatchIn(text)) return null
        return text.split(lineBreak)
            .mapNotNull { line -> cleanTitle(line.trim().replaceFirst(marker, "")) }
            .take(MAX_PASTED)
    }

    /**
     * [orderedIds] with [id] moved [delta] places (negative = up), or null when it is not there or cannot move that far.
     */
    fun move(orderedIds: List<Long>, id: Long, delta: Int): List<Long>? {
        val from = orderedIds.indexOf(id)
        val to = from + delta
        if (from < 0 || delta == 0 || to !in orderedIds.indices) return null
        return orderedIds.toMutableList().apply { add(to, removeAt(from)) }
    }
}
