package com.riyadmondol2006.taskfern.widget

import com.riyadmondol2006.taskfern.data.db.WidgetKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * One in-memory version counter per widget kind. Glance does not re-run `provideGlance` when `update()` reaches a
 * session that is still alive, so a live session watches its kind's counter and reloads when [WidgetUpdater] bumps it.
 * A session that has already closed simply starts again and reads fresh data. Nothing here touches the database.
 */
object WidgetSignals {
    val ALL_KINDS: Set<String> = setOf(
        WidgetKind.TODAY, WidgetKind.QUICK_ADD, WidgetKind.SHOPPING, WidgetKind.AGENDA, WidgetKind.ROUTINE,
    )

    private val versions: Map<String, MutableStateFlow<Long>> = ALL_KINDS.associateWith { MutableStateFlow(0L) }

    fun flow(kind: String): StateFlow<Long> = versions.getValue(kind)

    fun version(kind: String): Long = versions.getValue(kind).value

    fun bump(kind: String) {
        versions[kind]?.update { it + 1 }
    }
}
