package com.riyadmondol2006.taskfern

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.riyadmondol2006.taskfern.ui.navigation.TaskfernAppRoot
import com.riyadmondol2006.taskfern.ui.theme.TaskfernTheme

class MainActivity : ComponentActivity() {
    private var pendingLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingLink = intent?.data
        val settingsFlow = (application as TaskfernApp).container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            val s = settings ?: return@setContent // first frame waits for preferences: no theme flash
            TaskfernTheme(s.themeMode, s.dynamicColor, s.amoledBlack, s.accent) {
                TaskfernAppRoot(pendingDeepLink = pendingLink, onDeepLinkHandled = { pendingLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { pendingLink = it }
    }
}
