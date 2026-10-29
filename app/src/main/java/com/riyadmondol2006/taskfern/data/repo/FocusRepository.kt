package com.riyadmondol2006.taskfern.data.repo

import com.riyadmondol2006.taskfern.data.db.FocusSessionEntity
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase

/** Local history of focus-timer sessions. */
class FocusRepository(db: TaskfernDatabase) {
    private val dao = db.focusDao()

    fun observeRecent(limit: Int = 20) = dao.observeRecent(limit)
    fun observeFocusedSecondsSince(since: Long) = dao.observeFocusedSecondsSince(since)
    suspend fun start(taskId: Long?, startedAt: Long, plannedMinutes: Int): Long =
        dao.insert(FocusSessionEntity(taskId = taskId, startedAt = startedAt, plannedMinutes = plannedMinutes))

    suspend fun finish(id: Long, focusedSeconds: Long, completed: Boolean) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(focusedSeconds = focusedSeconds.coerceAtLeast(0), completed = completed))
    }
}
