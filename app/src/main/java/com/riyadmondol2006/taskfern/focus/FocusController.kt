package com.riyadmondol2006.taskfern.focus

import android.content.Context
import androidx.core.content.edit
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.repo.FocusRepository
import com.riyadmondol2006.taskfern.reminders.Notifications
import com.riyadmondol2006.taskfern.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The focus timer's state. Remaining time is derived from timestamps, so nothing ticks in the background. */
data class FocusState(
    val sessionId: Long = 0,
    val taskId: Long? = null,
    val taskTitle: String = "",
    val plannedMinutes: Int = 25,
    /** Wall-clock end when running; null when paused or idle. */
    val endsAt: Long? = null,
    /** Milliseconds left when paused. */
    val pausedRemainingMs: Long? = null,
) {
    val isRunning: Boolean get() = endsAt != null
    val isPaused: Boolean get() = pausedRemainingMs != null
    val isActive: Boolean get() = isRunning || isPaused
    fun remainingMs(now: Long): Long = when {
        endsAt != null -> (endsAt - now).coerceAtLeast(0)
        pausedRemainingMs != null -> pausedRemainingMs
        else -> plannedMinutes * 60_000L
    }
}

/**
 * A simple task-linked focus timer. No foreground service: the end is an alarm, the state survives process death in
 * SharedPreferences, and the screen computes the countdown from the end timestamp.
 */
class FocusController(
    private val context: Context,
    private val repo: FocusRepository,
    private val scheduler: ReminderScheduler,
) {
    private val prefs = context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    suspend fun start(minutes: Int, taskId: Long?, taskTitle: String) {
        if (_state.value.isActive) stop(completed = false)
        val now = System.currentTimeMillis()
        val id = repo.start(taskId, now, minutes)
        val s = FocusState(id, taskId, taskTitle, minutes, endsAt = now + minutes * 60_000L)
        save(s)
        scheduler.scheduleFocusEnd(s.endsAt!!)
    }

    fun pause() {
        val s = _state.value
        val ends = s.endsAt ?: return
        scheduler.cancelFocusEnd()
        save(s.copy(endsAt = null, pausedRemainingMs = (ends - System.currentTimeMillis()).coerceAtLeast(0)))
    }

    fun resume() {
        val s = _state.value
        val left = s.pausedRemainingMs ?: return
        val ends = System.currentTimeMillis() + left
        save(s.copy(endsAt = ends, pausedRemainingMs = null))
        scheduler.scheduleFocusEnd(ends)
    }

    /** Ends the session early ([completed] = false) or on time. Records the focused time in the local history. */
    suspend fun stop(completed: Boolean) {
        val s = _state.value
        if (!s.isActive) return
        scheduler.cancelFocusEnd()
        val focusedMs = s.plannedMinutes * 60_000L - s.remainingMs(System.currentTimeMillis())
        repo.finish(s.sessionId, focusedMs / 1000, completed)
        save(FocusState(plannedMinutes = s.plannedMinutes))
    }

    /** Called by the alarm when the time is up. */
    suspend fun onTimerAlarm() {
        val s = _state.value
        val ends = s.endsAt ?: return
        if (ends > System.currentTimeMillis() + 2_000) { // early or stale alarm
            scheduler.scheduleFocusEnd(ends)
            return
        }
        stop(completed = true)
        Notifications.showFocusDone(
            context,
            context.getString(R.string.focus_done_title),
            if (s.taskTitle.isNotEmpty()) context.getString(R.string.focus_done_text_task, s.plannedMinutes, s.taskTitle)
            else context.getString(R.string.focus_done_text, s.plannedMinutes),
        )
    }

    private fun save(s: FocusState) {
        _state.value = s
        prefs.edit {
            putLong("session", s.sessionId)
            putLong("task", s.taskId ?: -1)
            putString("title", s.taskTitle)
            putInt("minutes", s.plannedMinutes)
            putLong("ends", s.endsAt ?: -1)
            putLong("paused", s.pausedRemainingMs ?: -1)
        }
    }

    private fun load() = FocusState(
        sessionId = prefs.getLong("session", 0),
        taskId = prefs.getLong("task", -1).takeIf { it >= 0 },
        taskTitle = prefs.getString("title", "").orEmpty(),
        plannedMinutes = prefs.getInt("minutes", 25),
        endsAt = prefs.getLong("ends", -1).takeIf { it >= 0 },
        pausedRemainingMs = prefs.getLong("paused", -1).takeIf { it >= 0 },
    )
}
