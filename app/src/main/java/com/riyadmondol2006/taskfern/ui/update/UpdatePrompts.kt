package com.riyadmondol2006.taskfern.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.BuildConfig
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.settings.openWebPage
import com.riyadmondol2006.taskfern.update.ReleaseInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdatePromptsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val offer = c.updates.offer

    /** Written in the app scope so the answer is stored even if the activity goes away right after the tap. */
    fun setUpdateCheck(enabled: Boolean) {
        c.appScope.launch { c.settings.setUpdateCheck(enabled) }
    }

    fun checkAutomaticallyIfDue() = c.updates.checkAutomaticallyIfDue()
    fun later() = c.updates.dismiss()
    fun skip(info: ReleaseInfo) = c.updates.skip(info)
}

/**
 * Hosted once by the root UI: asks the one-time update question on release builds, runs the daily automatic check
 * when the user agreed, and shows the update popup (also opened by "Check now" in Settings).
 */
@Composable
fun UpdatePrompts() {
    val vm = taskfernViewModel { c, _ -> UpdatePromptsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val offer by vm.offer.collectAsStateWithLifecycle()
    // Back on the question means "not now": it is asked again on the next start, not answered for the user.
    var consentPostponed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.about_no_browser)
    val s = settings ?: return

    LaunchedEffect(s.updateCheck) {
        if (s.updateCheck == true) vm.checkAutomaticallyIfDue()
    }

    val release = offer
    when {
        release != null -> UpdateAvailableDialog(
            release = release,
            onDownload = {
                // The browser downloads the APK; Taskfern never downloads or installs anything itself.
                if (!context.openWebPage(release.downloadUrl)) scope.showMessage(snackbar, noBrowser)
                vm.later()
            },
            onLater = vm::later,
            onSkip = { vm.skip(release) },
        )
        s.updateCheck == null && !BuildConfig.DEBUG && !consentPostponed -> UpdateConsentDialog(
            onAnswer = vm::setUpdateCheck,
            onPostpone = { consentPostponed = true },
        )
    }
}

@Composable
private fun UpdateConsentDialog(onAnswer: (Boolean) -> Unit, onPostpone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onPostpone,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.Update, contentDescription = null) },
        title = { Text(stringResource(R.string.update_consent_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_consent_text))
                Text(
                    stringResource(R.string.update_consent_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.update_consent_on)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.update_consent_off)) } },
    )
}

@Composable
private fun UpdateAvailableDialog(release: ReleaseInfo, onDownload: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_available_title, release.versionName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.update_available_installed, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (release.notes.isNotEmpty()) {
                    Text(release.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    stringResource(R.string.update_available_install_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text(stringResource(R.string.update_download)) } },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip)) }
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
        },
    )
}
