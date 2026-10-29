package com.riyadmondol2006.taskfern.ui.quickadd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.ui.theme.TaskfernTheme

/**
 * Quick Add as its own small activity, shown over the home screen or another app. Opened by the Quick Add widget, the
 * "New task" launcher shortcut, and the Android share sheet (shared text becomes the task title).
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val defaults = defaultsFrom(intent)
        val settingsFlow = (application as TaskfernApp).container.settings.settings
        setContent {
            val s by settingsFlow.collectAsState(initial = Settings())
            TaskfernTheme(s.themeMode, s.dynamicColor, s.amoledBlack, s.accent) {
                QuickAddSheet(defaults = defaults, onDismiss = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.riyadmondol2006.taskfern.action.QUICK_ADD"
        const val EXTRA_LIST_ID = "list_id"
        /** "today", "tomorrow" or absent. */
        const val EXTRA_DUE = "due"

        fun defaultsFrom(intent: Intent?): QuickAddDefaults {
            if (intent == null) return QuickAddDefaults()
            val shared = if (intent.action == Intent.ACTION_SEND) {
                listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                    .joinToString(" ").replace(Regex("\\s+"), " ").trim().take(500)
            } else ""
            val due = intent.getStringExtra(EXTRA_DUE)
            return QuickAddDefaults(
                listId = intent.getLongExtra(EXTRA_LIST_ID, 0).takeIf { it > 0 },
                dueToday = due == "today",
                dueTomorrow = due == "tomorrow",
                text = shared,
            )
        }
    }
}
