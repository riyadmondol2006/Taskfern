package com.riyadmondol2006.taskfern.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * Refreshes home-screen widgets after data changes (event driven, no polling).
 * TODO(widgets): implemented in the widget package.
 */
class WidgetUpdater(private val context: Context, private val scope: CoroutineScope) {
    fun tasksChanged() {}
    fun shoppingChanged() {}
    fun routinesChanged() {}
    suspend fun refreshAll() {}
}
