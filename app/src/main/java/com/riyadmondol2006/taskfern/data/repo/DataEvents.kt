package com.riyadmondol2006.taskfern.data.repo

/**
 * Side effects that follow a data change: reminders to re-arm and widgets to refresh. Repositories call these after a
 * write commits, so alarms and widgets never run ahead of the database. Tests pass [None].
 */
interface DataEvents {
    /** Tasks were created, edited, completed or deleted. Empty [taskIds] means "anything may have changed". */
    fun tasksChanged(taskIds: Collection<Long> = emptyList()) {}
    fun shoppingChanged(listIds: Collection<Long> = emptyList()) {}
    fun routinesChanged(routineIds: Collection<Long> = emptyList()) {}

    object None : DataEvents
}
