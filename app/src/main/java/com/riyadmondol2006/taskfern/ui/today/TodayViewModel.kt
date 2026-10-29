package com.riyadmondol2006.taskfern.ui.today

import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.data.db.Priority
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.ui.tasks.TaskActionsViewModel
import com.riyadmondol2006.taskfern.ui.tasks.TaskFilters
import com.riyadmondol2006.taskfern.data.prefs.TaskSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class RoutineSummary(val id: Long, val name: String, val emoji: String, val total: Int, val done: Int)

data class TodayState(
    val loaded: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val userName: String = "",
    val overdue: List<TaskRow> = emptyList(),
    val dueToday: List<TaskRow> = emptyList(),
    val important: List<TaskRow> = emptyList(),
    val upcomingReminders: List<TaskRow> = emptyList(),
    val completedToday: List<TaskRow> = emptyList(),
    val routines: List<RoutineSummary> = emptyList(),
    val showCompleted: Boolean = true,
) {
    val remaining: Int get() = overdue.size + dueToday.size
    val routinesLeft: Int get() = routines.count { it.total > 0 && it.done < it.total }
    val hasReminders: Boolean get() = (overdue + dueToday).any { it.reminderCount > 0 } || upcomingReminders.isNotEmpty()
}

class TodayViewModel(
    tasks: TaskRepository,
    private val routines: RoutineRepository,
    settings: SettingsRepository,
) : TaskActionsViewModel(tasks) {

    /** Re-evaluated whenever the screen resumes, so the dashboard rolls over to a new day. */
    private val day = MutableStateFlow(LocalDate.now())

    fun refreshDay() {
        day.value = LocalDate.now()
    }

    private val taskPart = day.flatMapLatest { today ->
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        combine(
            tasks.observeDueOnOrBefore(today),
            tasks.observeCompletedSince(startOfDay),
            tasks.observeOpenRows(),
        ) { due, completed, open -> Triple(due, completed, open) }
    }

    private val routinePart = day.flatMapLatest { today ->
        combine(routines.observeRoutines(), routines.observeAllSteps(), routines.observeChecksOn(today)) { rs, steps, checks ->
            val stepCount = steps.groupingBy { it.routineId }.eachCount()
            val doneCount = checks.groupingBy { it.routineId }.eachCount()
            rs.filter { !it.paused && RoutineRepository.runsOn(it, today) && (stepCount[it.id] ?: 0) > 0 }
                .map { RoutineSummary(it.id, it.name, it.emoji, stepCount[it.id] ?: 0, doneCount[it.id] ?: 0) }
        }
    }

    val state: StateFlow<TodayState> = combine(day, taskPart, routinePart, settings.settings) { today, (due, completed, open), rs, s: Settings ->
        val sorted = TaskFilters.sort(due, TaskSort.SMART)
        val overdue = sorted.filter { it.task.dueDate!!.isBefore(today) }
        val dueToday = sorted.filter { it.task.dueDate == today }
        val shownIds = due.mapTo(HashSet()) { it.task.id }
        val now = System.currentTimeMillis()
        val dayAhead = now + 24 * 60 * 60 * 1000L
        TodayState(
            loaded = true,
            date = today,
            userName = s.userName,
            overdue = overdue,
            dueToday = dueToday,
            important = open.filter { it.task.priority == Priority.HIGH && it.task.id !in shownIds && it.task.recurrence?.paused != true }
                .let { TaskFilters.sort(it, TaskSort.SMART) }.take(3),
            upcomingReminders = open.filter { r ->
                r.task.id !in shownIds && r.task.nextReminderAt?.let { it in now..dayAhead } == true
            }.sortedBy { it.task.nextReminderAt }.take(3),
            completedToday = completed,
            routines = rs,
            showCompleted = s.showCompletedToday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun moveOverdueToToday() {
        val ids = state.value.overdue.map { it.task.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { tasks.setDueDate(ids, LocalDate.now()) }
    }
}
