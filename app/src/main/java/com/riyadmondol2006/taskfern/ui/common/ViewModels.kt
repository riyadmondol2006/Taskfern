package com.riyadmondol2006.taskfern.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.TaskfernApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Creates a ViewModel with access to the app's [AppContainer] and a [SavedStateHandle] (navigation arguments). */
@Composable
inline fun <reified VM : ViewModel> taskfernViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as TaskfernApp
            create(app.container, createSavedStateHandle())
        }
    },
)

/** App-wide snackbar host, provided by the scaffold. */
val LocalSnackbarHost = staticCompositionLocalOf { SnackbarHostState() }

/** Shows a snackbar with an Undo action and runs [onUndo] if it is pressed. */
fun CoroutineScope.showUndo(host: SnackbarHostState, message: String, undoLabel: String, onUndo: suspend () -> Unit) {
    launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(message, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}

fun CoroutineScope.showMessage(host: SnackbarHostState, message: String) {
    launch {
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(message, withDismissAction = true)
    }
}
