package com.riyadmondol2006.taskfern.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.BuildConfig
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator

/** A runtime library bundled in the app and its licence. */
private data class Library(val name: String, val artifact: String)

/** Every runtime dependency from gradle/libs.versions.toml. All are published under the Apache License 2.0. */
private val LIBRARIES = listOf(
    Library("Android Jetpack Core KTX", "androidx.core:core-ktx"),
    Library("Android Jetpack Core SplashScreen", "androidx.core:core-splashscreen"),
    Library("Activity Compose", "androidx.activity:activity-compose"),
    Library("Lifecycle (runtime, Compose, ViewModel)", "androidx.lifecycle:lifecycle-*"),
    Library("Navigation Compose", "androidx.navigation:navigation-compose"),
    Library("Jetpack Compose UI, Foundation and Animation", "androidx.compose.ui, foundation, animation"),
    Library("Compose Material 3 and adaptive navigation", "androidx.compose.material3:material3*"),
    Library("Compose Material Icons Extended", "androidx.compose.material:material-icons-extended"),
    Library("Room", "androidx.room:room-runtime, room-ktx"),
    Library("DataStore Preferences", "androidx.datastore:datastore-preferences"),
    Library("WorkManager", "androidx.work:work-runtime-ktx"),
    Library("Glance App Widgets", "androidx.glance:glance-appwidget, glance-material3"),
    Library("Kotlin Standard Library", "org.jetbrains.kotlin:kotlin-stdlib"),
    Library("kotlinx.coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-android"),
    Library("kotlinx.serialization JSON", "org.jetbrains.kotlinx:kotlinx-serialization-json"),
)

@Composable
fun AboutScreen(nav: AppNavigator) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.about_no_browser)
    fun open(url: String) {
        if (!context.openWebPage(url)) scope.showMessage(snackbar, noBrowser)
    }
    val repo = BuildConfig.REPO_URL.trimEnd('/')

    SettingsScaffold(title = stringResource(R.string.about_title), onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 32.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)).background(colorResource(R.color.ic_launcher_background)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(R.drawable.ic_taskfern_mark),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color(0xFFF7F8F3)),
                        modifier = Modifier.size(92.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.about_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }

            SettingsGroup(stringResource(R.string.about_privacy_title)) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.about_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            SettingsGroup(stringResource(R.string.about_project)) {
                LinkRow(Icons.Outlined.Code, stringResource(R.string.about_source), repo) { open(repo) }
                LinkRow(Icons.Outlined.NewReleases, stringResource(R.string.about_releases), "$repo/releases") { open("$repo/releases") }
                LinkRow(Icons.Outlined.BugReport, stringResource(R.string.about_issues), "$repo/issues") { open("$repo/issues") }
            }

            SettingsGroup(stringResource(R.string.about_license_title)) {
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            SettingsGroup(stringResource(R.string.about_oss_title)) {
                Text(
                    stringResource(R.string.about_oss_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                LIBRARIES.forEachIndexed { i, lib ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}) {
                        Text(lib.name, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                lib.artifact,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.about_apache), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                LinkRow(Icons.Outlined.Code, stringResource(R.string.about_apache_full), APACHE_URL) { open(APACHE_URL) }
            }
        }
    }
}

private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, url: String, onClick: () -> Unit) {
    SettingsItem(
        title = title,
        summary = url.removePrefix("https://"),
        icon = icon,
        onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
