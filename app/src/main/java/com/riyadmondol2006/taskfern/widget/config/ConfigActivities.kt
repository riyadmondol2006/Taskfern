package com.riyadmondol2006.taskfern.widget.config

import android.content.Context
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingListEntity
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.WidgetConfigEntity
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import com.riyadmondol2006.taskfern.widget.QuickAddDue
import com.riyadmondol2006.taskfern.widget.QuickAddWidgetReceiver
import com.riyadmondol2006.taskfern.widget.RoutineWidgetReceiver
import com.riyadmondol2006.taskfern.widget.ShoppingWidgetReceiver
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** What a configuration screen needs: the choices and this instance's saved row, if any. */
private class Loaded<T>(val options: List<T>, val config: WidgetConfigEntity?)

/** Quick Add widget: default list (Inbox or a task list) and default due date. */
class QuickAddWidgetConfigActivity : WidgetConfigActivity() {
    override val kind = WidgetKind.QUICK_ADD
    override val receiverClass: Class<*> = QuickAddWidgetReceiver::class.java

    @Composable
    override fun ConfigContent() {
        var list by rememberSaveable { mutableLongStateOf(UNSET) }
        var due by rememberSaveable { mutableStateOf<String?>(null) }
        val loaded by produceState<Loaded<TaskListEntity>?>(null) {
            val l = Loaded(container.tasks.getLists(), container.database.widgetConfigDao().get(appWidgetId))
            val saved = l.config?.takeIf { it.kind == kind }
            if (list == UNSET) list = initialSelection(saved, l.options.map { it.id })
            if (due == null) due = QuickAddDue.fromKey(saved?.option).key
            value = l
        }
        val dueChoice = QuickAddDue.fromKey(due)
        ConfigScaffold(
            title = stringResource(R.string.widget_config_quick_add_title),
            loading = loaded == null,
            onClose = ::finish,
            onSave = { save(list.toTargetId(), dueChoice.key) },
        ) {
            sectionHeader(getString(R.string.widget_config_list_section), "list-header")
            item(key = "inbox") {
                OptionRow(stringResource(R.string.view_inbox), list == DEFAULT_TARGET, { list = DEFAULT_TARGET })
            }
            items(loaded?.options.orEmpty(), key = { "list-${it.id}" }) { option ->
                OptionRow(option.name, list == option.id, { list = option.id })
            }
            sectionHeader(getString(R.string.widget_config_due_section), "due-header")
            items(QuickAddDue.entries, key = { "due-${it.key}" }) { option ->
                val label = when (option) {
                    QuickAddDue.NONE -> stringResource(R.string.no_date)
                    QuickAddDue.TODAY -> stringResource(R.string.today)
                    QuickAddDue.TOMORROW -> stringResource(R.string.tomorrow)
                }
                OptionRow(label, dueChoice == option, { due = option.key })
            }
        }
    }
}

/** Shopping widget: which list this instance shows. */
class ShoppingWidgetConfigActivity : WidgetConfigActivity() {
    override val kind = WidgetKind.SHOPPING
    override val receiverClass: Class<*> = ShoppingWidgetReceiver::class.java

    @Composable
    override fun ConfigContent() {
        var selected by rememberSaveable { mutableLongStateOf(UNSET) }
        val loaded by produceState<Loaded<ShoppingListEntity>?>(null) {
            val l = Loaded(container.shopping.getLists(), container.database.widgetConfigDao().get(appWidgetId))
            if (selected == UNSET) selected = initialSelection(l.config?.takeIf { it.kind == kind }, l.options.map { it.id })
            value = l
        }
        ConfigScaffold(
            title = stringResource(R.string.widget_config_shopping_title),
            loading = loaded == null,
            onClose = ::finish,
            onSave = { save(selected.toTargetId()) },
        ) {
            sectionHeader(getString(R.string.widget_config_shopping_section), "header")
            item(key = "first") {
                OptionRow(stringResource(R.string.widget_config_first_list), selected == DEFAULT_TARGET, { selected = DEFAULT_TARGET })
            }
            items(loaded?.options.orEmpty(), key = { "list-${it.id}" }) { option ->
                OptionRow(option.name, selected == option.id, { selected = option.id })
            }
            if (loaded?.options?.isEmpty() == true) note(getString(R.string.widget_config_no_shopping), "empty")
        }
    }
}

/** Routine widget: which routine this instance shows. */
class RoutineWidgetConfigActivity : WidgetConfigActivity() {
    override val kind = WidgetKind.ROUTINE
    override val receiverClass: Class<*> = RoutineWidgetReceiver::class.java

    @Composable
    override fun ConfigContent() {
        var selected by rememberSaveable { mutableLongStateOf(UNSET) }
        val loaded by produceState<Loaded<RoutineEntity>?>(null) {
            val l = Loaded(container.routines.getRoutines(), container.database.widgetConfigDao().get(appWidgetId))
            if (selected == UNSET) selected = initialSelection(l.config?.takeIf { it.kind == kind }, l.options.map { it.id })
            value = l
        }
        val context = LocalContext.current
        ConfigScaffold(
            title = stringResource(R.string.widget_config_routine_title),
            loading = loaded == null,
            onClose = ::finish,
            onSave = { save(selected.toTargetId()) },
        ) {
            sectionHeader(getString(R.string.widget_config_routine_section), "header")
            item(key = "first") {
                OptionRow(stringResource(R.string.widget_config_first_routine), selected == DEFAULT_TARGET, { selected = DEFAULT_TARGET })
            }
            items(loaded?.options.orEmpty(), key = { "routine-${it.id}" }) { routine ->
                OptionRow(
                    label = routine.name,
                    selected = selected == routine.id,
                    onSelect = { selected = routine.id },
                    supporting = scheduleSummary(context, routine),
                    leading = routine.emoji,
                )
            }
            if (loaded?.options?.isEmpty() == true) note(getString(R.string.widget_config_no_routines), "empty")
        }
    }
}

/** The saved target when it still exists, else the default (Inbox / first list / first routine). */
private fun initialSelection(saved: WidgetConfigEntity?, available: List<Long>): Long =
    saved?.targetId?.takeIf { it in available } ?: DEFAULT_TARGET

/** "Every day", "Mon, Wed, Fri", "No days", plus "Paused" when it is. */
private fun scheduleSummary(context: Context, routine: RoutineEntity): String {
    val days = when (val mask = routine.weekdays and 0x7F) {
        0x7F -> context.getString(R.string.widget_config_every_day)
        0 -> context.getString(R.string.widget_config_no_days)
        else -> DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    return if (routine.paused) "$days · ${context.getString(R.string.paused)}" else days
}
