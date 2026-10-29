package com.riyadmondol2006.taskfern.widget.config

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.data.db.WidgetConfigEntity
import com.riyadmondol2006.taskfern.ui.theme.TaskfernTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Base of the widget configuration screens. Started by the launcher with [AppWidgetManager.EXTRA_APPWIDGET_ID] when a
 * widget is placed or reconfigured (or by the widget's own "choose another" banner). Leaving without saving returns
 * RESULT_CANCELED, which tells the launcher to drop a widget that is still being placed.
 */
abstract class WidgetConfigActivity : ComponentActivity() {
    /** One of [com.riyadmondol2006.taskfern.data.db.WidgetKind]. */
    protected abstract val kind: String

    /** The provider this screen configures; ids of other providers are refused. */
    protected abstract val receiverClass: Class<*>

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    protected val container: AppContainer get() = (application as TaskfernApp).container

    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, resultIntent())
        if (!isOwnWidget(appWidgetId)) {
            finish()
            return
        }
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            val s = settings ?: return@setContent
            TaskfernTheme(s.themeMode, s.dynamicColor, s.amoledBlack, s.accent) {
                ConfigContent()
            }
        }
    }

    @Composable
    protected abstract fun ConfigContent()

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun isOwnWidget(id: Int): Boolean {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val info = AppWidgetManager.getInstance(this)?.getAppWidgetInfo(id) ?: return false
        return info.provider == ComponentName(this, receiverClass)
    }

    /** Persists this instance's choice, redraws the widget and hands control back to the launcher. */
    protected suspend fun save(targetId: Long?, option: String = "") {
        if (saving) return
        saving = true
        try {
            container.database.widgetConfigDao().upsert(WidgetConfigEntity(appWidgetId, kind, targetId, option))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saving = false
            Log.w("TaskfernWidgets", "Could not save widget $appWidgetId", e)
            Toast.makeText(this, R.string.widget_config_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        container.widgets.refreshInstance(kind, appWidgetId)
        setResult(RESULT_OK, resultIntent())
        finish()
    }
}

/** Sentinel for "not chosen yet" in the selection state; real ids are positive and 0 means the default. */
internal const val UNSET = -1L

/** Selection value meaning Inbox / first list / first routine. */
internal const val DEFAULT_TARGET = 0L

internal fun Long.toTargetId(): Long? = takeIf { it > 0 }

@Composable
internal fun ConfigScaffold(
    title: String,
    loading: Boolean,
    onClose: () -> Unit,
    onSave: suspend () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        enabled = !loading && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    onSave()
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                content = content,
            )
        }
    }
}

internal fun LazyListScope.sectionHeader(text: String, key: String) {
    item(key = key) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
        )
    }
}

internal fun LazyListScope.note(text: String, key: String) {
    item(key = key) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

/** A full-width radio option; the whole row is the touch target. */
@Composable
internal fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit, supporting: String? = null, leading: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        if (leading != null) {
            Text(leading, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
