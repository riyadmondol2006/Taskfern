package com.riyadmondol2006.taskfern.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.RoutineDraft
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.StepDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A step row in the editor. [key] is stable for the row's lifetime; [id] is 0 for steps not saved yet. */
data class EditableStep(val key: Long, val id: Long, val title: String)

data class RoutineForm(
    val name: String = "",
    val emoji: String = "🌿",
    val color: Int = 0,
    val weekdays: Int = WeekdayMask.EVERY_DAY,
    val timeMinutes: Int? = null,
    val steps: List<EditableStep> = emptyList(),
) {
    val cleanSteps: List<EditableStep> get() = steps.filter { it.title.isNotBlank() }
    val nameError: Boolean get() = name.isBlank()
    val stepsError: Boolean get() = cleanSteps.isEmpty()
    val daysError: Boolean get() = WeekdayMask.count(weekdays) == 0
    val isValid: Boolean get() = !nameError && !stepsError && !daysError

    /** What would be saved; used to tell whether anything changed. */
    fun toDraft(): RoutineDraft = RoutineDraft(
        name = name.trim(),
        emoji = emoji.ifBlank { "🌿" },
        color = color,
        weekdays = weekdays,
        timeMinutes = timeMinutes,
        steps = cleanSteps.map { StepDraft(it.id, it.title.trim()) },
    )
}

class RoutineEditorViewModel(
    private val routines: RoutineRepository,
    settings: SettingsRepository,
    private val routineId: Long,
) : ViewModel() {
    val isNew: Boolean = routineId == 0L

    private val _form = MutableStateFlow(RoutineForm())
    val form: StateFlow<RoutineForm> = _form.asStateFlow()

    private val _loaded = MutableStateFlow(isNew)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** True when the routine being edited no longer exists (deleted elsewhere). */
    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    /** Set after a save is attempted, so errors only appear once they are useful. */
    private val _showErrors = MutableStateFlow(false)
    val showErrors: StateFlow<Boolean> = _showErrors.asStateFlow()

    private var initial: RoutineDraft = RoutineForm().toDraft()
    private var nextKey = -1L
    private var saving = false

    val weekStartsMonday: StateFlow<Boolean> = settings.settings.map { it.weekStartsMonday }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        if (!isNew) {
            viewModelScope.launch {
                val routine = routines.getRoutine(routineId)
                if (routine != null) {
                    val steps = routines.getSteps(routineId)
                    val loadedForm = RoutineForm(
                        name = routine.name,
                        emoji = routine.emoji,
                        color = routine.color,
                        weekdays = routine.weekdays,
                        timeMinutes = routine.timeMinutes,
                        steps = steps.map { EditableStep(key = it.id, id = it.id, title = it.title) },
                    )
                    initial = loadedForm.toDraft()
                    _form.value = loadedForm
                } else {
                    _missing.value = true
                }
                _loaded.value = true
            }
        }
    }

    val hasChanges: Boolean get() = _form.value.toDraft() != initial

    fun setName(name: String) = _form.update { it.copy(name = name.replace("\n", " ").take(60)) }
    fun setEmoji(emoji: String) = _form.update { it.copy(emoji = emoji) }
    fun setColor(color: Int) = _form.update { it.copy(color = color) }
    fun setWeekdays(mask: Int) = _form.update { it.copy(weekdays = mask and WeekdayMask.EVERY_DAY) }
    fun setTime(minutes: Int?) = _form.update { it.copy(timeMinutes = minutes) }

    /** Adds one step per non-blank line (so a pasted list becomes several steps). */
    fun addSteps(text: String) {
        val lines = text.lines().map { it.trim().take(120) }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        _form.update { f -> f.copy(steps = f.steps + lines.map { EditableStep(nextKey--, 0, it) }) }
    }

    fun renameStep(key: Long, title: String) = _form.update { f ->
        f.copy(steps = f.steps.map { if (it.key == key) it.copy(title = title.replace("\n", " ").take(120)) else it })
    }

    fun deleteStep(key: Long) = _form.update { f -> f.copy(steps = f.steps.filterNot { it.key == key }) }

    fun moveStep(key: Long, delta: Int) = _form.update { f ->
        val from = f.steps.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in f.steps.indices) f
        else f.copy(steps = f.steps.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Validates and saves. Calls [onSaved] with the routine id; on invalid input the errors become visible instead. */
    fun save(onSaved: (Long) -> Unit) {
        val f = _form.value
        if (!f.isValid) {
            _showErrors.value = true
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            val draft = f.toDraft()
            val id = if (isNew) routines.create(draft) else routineId.also { routines.update(it, draft) }
            initial = draft
            onSaved(id)
        }
    }
}
