package com.riyadmondol2006.taskfern.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.riyadmondol2006.taskfern.reminders.Notifications

/**
 * Returns a function that asks for the notification permission (Android 13+) the first time the user sets up a
 * reminder, i.e. when the request makes sense to them. Does nothing when already granted or on older Android. If the
 * user declines, reminders still exist in the app and Today/Settings explain how to turn notifications on.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
