package com.riyadmondol2006.taskfern.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.data.db.OccurrenceLogEntity
import com.riyadmondol2006.taskfern.data.db.SubtaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.CompletionUndo
import com.riyadmondol2006.taskfern.data.repo.TaskDraft
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import com.riyadmondol2006.taskfern.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

/** Editable copy of a task's fields. Saved when leaving the screen (and when the app goes to the background). */
data class EditorDraft(
    val title: String = "",
    val notes: String = "",
    val listId: Long? = null,
    val priority: Int = 0,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val recurrence: RecurrenceRule? = null,
    val reminders: List<Int> = emptyList(),
) {
    fun toTaskDraft() = TaskDraft(
        title = title, notes = notes, listId = listId, priority = priority, dueDate = dueDate, dueTime = dueTime,
        tags = tags, pinned = pinned, recurrence = recurrence, reminderOffsets = reminders,
    )
}

sealed interface EditorEvent {
    data class Completed(val undo: CompletionUndo, val nextDue: LocalDate?) : EditorEvent
    data class Skipped(val undo: CompletionUndo, val nextDue: LocalDate?) : EditorEvent
    data object Deleted : EditorEvent
    data class Duplicated(val id: Long) : EditorEvent
    data object Missing : EditorEvent
}

class TaskEditorViewModel(
    private val tasks: TaskRepository,
    private val settingsRepo: SettingsRepository,
    private val scheduler: ReminderScheduler,
    /** Process-wide scope: the final save on leaving must outlive this ViewModel. */
    private val appScope: CoroutineScope,
    val taskId: Long,
) : ViewModel() {

    private val _draft = MutableStateFlow(EditorDraft())
    val draft: StateFlow<EditorDraft> = _draft.asStateFlow()
    private var saved: EditorDraft? = null

    /** The stored task (completion state, anchor, timestamps) — live. */
    val task: StateFlow<TaskEntity?> = tasks.observeDetails(taskId).map { it?.task }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val subtasks: StateFlow<List<SubtaskEntity>> = tasks.observeDetails(taskId).map { d -> d?.subtasks?.sortedWith(compareBy({ it.position }, { it.id })) ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val log: StateFlow<List<OccurrenceLogEntity>> = tasks.observeLog(taskId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lists: StateFlow<List<TaskListEntity>> = tasks.observeLists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allTags: StateFlow<List<String>> = tasks.observeTags().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EditorEvent> = _events

    val exactAlarms: Boolean get() = scheduler.canScheduleExact()

    init {
        viewModelScope.launch {
            val details = tasks.getDetails(taskId)
            if (details == null) {
                _events.emit(EditorEvent.Missing)
                return@launch
            }
            val t = details.task
            val d = EditorDraft(
                title = t.title, notes = t.notes, listId = t.listId, priority = t.priority, dueDate = t.dueDate,
                dueTime = t.dueTime, tags = t.tagList, pinned = t.pinned, recurrence = t.recurrence,
                reminders = details.reminders.map { it.offsetMinutes }.sorted(),
            )
            saved = d
            _draft.value = d
            _loaded.value = true
        }
    }

    fun edit(change: (EditorDraft) -> EditorDraft) = _draft.update(change)

    fun setDueDate(date: LocalDate?) = _draft.update { d ->
        when {
            date == null && d.recurrence != null -> d // a repeating task needs a date; the UI disables "No date"
            date == null -> d.copy(dueDate = null, dueTime = null, reminders = emptyList())
            d.dueDate == null && d.reminders.isEmpty() && settings.value.autoReminder -> d.copy(dueDate = date, reminders = listOf(0))
            else -> d.copy(dueDate = date)
        }
    }

    fun toggleReminder(offset: Int) = _draft.update { d ->
        val list = if (offset in d.reminders) d.reminders - offset else (d.reminders + offset).sorted()
        d.copy(reminders = list, dueDate = d.dueDate ?: LocalDate.now())
    }

    fun setRecurrence(rule: RecurrenceRule?) = _draft.update { d ->
        d.copy(recurrence = rule, dueDate = if (rule != null) d.dueDate ?: LocalDate.now() else d.dueDate)
    }

    private val saveLock = Mutex()

    /** Writes the draft if it changed. Serialised, so a save can never land after a later completion or skip. */
    private suspend fun persist() = saveLock.withLock {
        val d = _draft.value
        if (!_loaded.value || d == saved || d.title.isBlank()) return@withLock // never save an empty title
        saved = d
        tasks.update(taskId, d.toTaskDraft())
    }

    /** Saves on leaving the screen or going to the background; runs on the app scope so it outlives this ViewModel. */
    fun save() {
        appScope.launch { persist() }
    }

    fun toggleComplete() {
        viewModelScope.launch {
            persist()
            val t = tasks.get(taskId) ?: return@launch
            if (t.isCompleted) {
                tasks.reopen(taskId)
            } else {
                val undo = tasks.complete(taskId) ?: return@launch
                val after = tasks.get(taskId)
                if (after != null && !after.isCompleted) {
                    // A repeating task moved on: reflect the new due date in the editor.
                    _draft.update { it.copy(dueDate = after.dueDate) }
                    saved = _draft.value
                }
                _events.emit(EditorEvent.Completed(undo, after?.takeIf { !it.isCompleted && t.isRecurring }?.dueDate))
            }
        }
    }

    fun undoCompletion(undo: CompletionUndo) {
        viewModelScope.launch {
            tasks.undoCompletion(undo)
            _draft.update { it.copy(dueDate = undo.before.dueDate) }
            saved = _draft.value
        }
    }

    fun skip() {
        viewModelScope.launch {
            persist()
            val undo = tasks.skipOccurrence(taskId) ?: return@launch
            val next = tasks.get(taskId)?.dueDate
            _draft.update { it.copy(dueDate = next) }
            saved = _draft.value
            _events.emit(EditorEvent.Skipped(undo, next))
        }
    }

    fun setPaused(paused: Boolean) {
        viewModelScope.launch {
            persist()
            tasks.setSeriesPaused(taskId, paused)
            val t = tasks.get(taskId) ?: return@launch
            _draft.update { it.copy(recurrence = t.recurrence, dueDate = t.dueDate) }
            saved = _draft.value
        }
    }

    fun delete() {
        saved = _draft.value // nothing more to save
        viewModelScope.launch {
            tasks.moveToTrash(listOf(taskId))
            _events.emit(EditorEvent.Deleted)
        }
    }

    fun restoreFromTrash() = viewModelScope.launch { tasks.restoreFromTrash(listOf(taskId)) }

    fun duplicate() {
        viewModelScope.launch {
            persist()
            tasks.duplicate(taskId)?.let { _events.emit(EditorEvent.Duplicated(it)) }
        }
    }

    fun archive(archived: Boolean) {
        viewModelScope.launch {
            persist()
            tasks.setArchived(listOf(taskId), archived)
        }
    }

    // Subtasks apply immediately.
    fun addSubtask(title: String) = viewModelScope.launch { tasks.addSubtask(taskId, title) }
    fun toggleSubtask(s: SubtaskEntity) = viewModelScope.launch { tasks.updateSubtask(s.copy(done = !s.done)) }
    fun renameSubtask(s: SubtaskEntity, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) tasks.updateSubtask(s.copy(title = title.trim()))
    }
    fun deleteSubtask(s: SubtaskEntity) = viewModelScope.launch { tasks.deleteSubtask(s) }
    fun moveSubtask(s: SubtaskEntity, delta: Int) = viewModelScope.launch {
        val list = subtasks.first().toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@launch
        list.add(j, list.removeAt(i))
        tasks.reorderSubtasks(list)
    }

    override fun onCleared() {
        save()
        super.onCleared()
    }
}
