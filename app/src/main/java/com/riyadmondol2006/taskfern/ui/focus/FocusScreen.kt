package com.riyadmondol2006.taskfern.ui.focus

import com.riyadmondol2006.taskfern.domain.LogicalDay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.FocusSessionEntity
import com.riyadmondol2006.taskfern.focus.FocusState
import com.riyadmondol2006.taskfern.reminders.Notifications
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.settings.SettingsScaffold
import com.riyadmondol2006.taskfern.ui.settings.TextFieldDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** A session that ended on time while (or just before) the screen was open. */
data class FinishedSession(val minutes: Int, val taskId: Long?, val taskTitle: String, val taskCompleted: Boolean = false)

data class FocusHistoryItem(val session: FocusSessionEntity, val taskTitle: String?)

/** The task offered for linking when the screen was opened from a task. */
data class LinkedTask(val id: Long, val title: String, val completed: Boolean)

class FocusViewModel(private val c: AppContainer, private val taskId: Long) : ViewModel() {
    val state: StateFlow<FocusState> = c.focus.state
    val defaultMinutes = c.settings.settings.map { it.focusMinutes }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val linkedTask: StateFlow<LinkedTask?> = (if (taskId > 0) c.tasks.observeRow(taskId) else emptyFlow())
        .map { row -> row?.task?.takeIf { it.deletedAt == null }?.let { LinkedTask(it.id, it.title, it.isCompleted) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Recent sessions, without the one that is still running. */
    val history: StateFlow<List<FocusHistoryItem>?> = combine(
        c.focusRepository.observeRecent(20),
        state.map { if (it.isActive) it.sessionId else 0L }.distinctUntilChanged(),
    ) { sessions, activeId ->
        val titles = sessions.mapNotNull { it.taskId }.distinct().associateWith { c.tasks.get(it)?.title }
        sessions.filter { it.id != activeId }.map { FocusHistoryItem(it, it.taskId?.let { id -> titles[id] }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _finished = MutableStateFlow<FinishedSession?>(null)
    val finished: StateFlow<FinishedSession?> = _finished.asStateFlow()

    init {
        // Notice a session that the end-of-time alarm closed while this screen existed.
        viewModelScope.launch {
            var last: FocusState? = null
            state.collect { s ->
                val prev = last
                val ends = prev?.endsAt
                if (prev != null && ends != null && !s.isActive && ends <= System.currentTimeMillis() + 2_000) {
                    _finished.value = FinishedSession(prev.plannedMinutes, prev.taskId, prev.taskTitle)
                }
                last = s
            }
        }
        // Opened from the "session complete" notification: offer the same follow-up for a session that just ended.
        viewModelScope.launch {
            if (state.value.isActive) return@launch
            val latest = c.focusRepository.observeRecent(1).first().firstOrNull() ?: return@launch
            val plannedEnd = latest.startedAt + latest.plannedMinutes * 60_000L
            val sinceEnd = System.currentTimeMillis() - plannedEnd
            if (latest.completed && sinceEnd in 0..RECENT_FINISH_MS && _finished.value == null) {
                val title = latest.taskId?.let { c.tasks.get(it) }
                _finished.value = FinishedSession(
                    latest.plannedMinutes, latest.taskId, title?.title.orEmpty(), taskCompleted = title?.isCompleted == true,
                )
            }
        }
    }

    fun start(minutes: Int) {
        val linked = linkedTask.value?.takeIf { !it.completed }
        _finished.value = null
        c.appScope.launch { c.focus.start(minutes, linked?.id, linked?.title.orEmpty()) }
    }

    fun pause() = c.focus.pause()
    fun resume() = c.focus.resume()

    fun stop() {
        c.appScope.launch { c.focus.stop(completed = false) }
    }

    /** Called every second while the screen is visible; ends the session in the app when its time is up. */
    fun finishIfDue(now: Long) {
        val s = state.value
        val ends = s.endsAt ?: return
        if (ends > now) return
        _finished.value = FinishedSession(s.plannedMinutes, s.taskId, s.taskTitle)
        c.appScope.launch { c.focus.stop(completed = true) }
    }

    fun completeTask(onDone: (String) -> Unit) {
        val f = _finished.value ?: return
        val id = f.taskId ?: return
        c.appScope.launch {
            val undo = c.tasks.complete(id)
            _finished.value = f.copy(taskCompleted = true)
            if (undo != null) onDone(f.taskTitle)
        }
    }

    fun dismissFinished() {
        _finished.value = null
    }

    private companion object {
        const val RECENT_FINISH_MS = 30 * 60_000L
    }
}

private val PRESETS = listOf(15, 25, 45, 60)

@Composable
fun FocusScreen(nav: AppNavigator, taskId: Long) {
    val vm = taskfernViewModel(key = "focus-$taskId") { c, _ -> FocusViewModel(c, taskId) }
    val focus by vm.state.collectAsStateWithLifecycle()
    val defaultMinutes by vm.defaultMinutes.collectAsStateWithLifecycle()
    val linked by vm.linkedTask.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    var customDialog by rememberSaveable { mutableStateOf(false) }
    val minutes = chosen ?: defaultMinutes ?: 25

    var canNotify by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifications.canPost(context)
        onPauseOrDispose { }
    }

    // Tick once per second, only while the screen is in the foreground.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(focus.endsAt, lifecycleOwner) {
        val ends = focus.endsAt
        now = System.currentTimeMillis()
        if (ends == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                vm.finishIfDue(now)
                val left = ends - now
                delay(if (left <= 0) 1_000 else (left % 1_000).let { if (it < 20) it + 1_000 else it })
            }
        }
    }

    val totalMs = (if (focus.isActive) focus.plannedMinutes else minutes) * 60_000L
    val remainingMs = if (focus.isActive) focus.remainingMs(now) else totalMs
    val fraction = if (totalMs > 0) remainingMs.toFloat() / totalMs else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (focus.isRunning) tween(1_000, easing = LinearEasing) else snap(),
        label = "focusRing",
    )

    SettingsScaffold(title = stringResource(R.string.focus_title), onBack = nav::back) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                val title = if (focus.isActive) focus.taskTitle else linked?.takeIf { !it.completed }?.title.orEmpty()
                if (title.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.widthIn(max = 360.dp).padding(top = 4.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                stringResource(R.string.focus_on_task, title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            item {
                Ring(
                    fraction = animated,
                    time = formatRemaining(remainingMs),
                    status = stringResource(
                        when {
                            focus.isRunning -> R.string.focus_status_running
                            focus.isPaused -> R.string.focus_status_paused
                            else -> R.string.focus_status_ready
                        },
                    ),
                    accessibleTime = pluralStringResource(
                        R.plurals.focus_minutes_left, ((remainingMs + 59_999) / 60_000).toInt(), ((remainingMs + 59_999) / 60_000).toInt(),
                    ),
                )
            }
            if (!focus.isActive) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        PRESETS.forEach { m ->
                            FilterChip(
                                selected = minutes == m,
                                onClick = { chosen = m },
                                label = { Text(pluralStringResource(R.plurals.focus_preset, m, m)) },
                            )
                        }
                        val custom = minutes !in PRESETS
                        FilterChip(
                            selected = custom,
                            onClick = { customDialog = true },
                            label = {
                                Text(
                                    if (custom) pluralStringResource(R.plurals.focus_custom_value, minutes, minutes)
                                    else stringResource(R.string.focus_custom),
                                )
                            },
                        )
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    when {
                        focus.isRunning -> {
                            FilledTonalButton(onClick = vm::pause) {
                                Icon(Icons.Outlined.Pause, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.focus_pause))
                            }
                            OutlinedButton(onClick = vm::stop) {
                                Icon(Icons.Outlined.Stop, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.focus_stop))
                            }
                        }
                        focus.isPaused -> {
                            Button(onClick = vm::resume) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.focus_resume))
                            }
                            OutlinedButton(onClick = vm::stop) {
                                Icon(Icons.Outlined.Stop, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.focus_stop))
                            }
                        }
                        else -> Button(onClick = { vm.start(minutes) }, modifier = Modifier.widthIn(min = 160.dp)) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.focus_start))
                        }
                    }
                }
            }
            if (!canNotify) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp, start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.focus_no_notifications),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        TextButton(onClick = nav::openSettings) { Text(stringResource(R.string.nav_settings)) }
                    }
                }
            }
            finished?.let { f ->
                item {
                    FinishedCard(
                        session = f,
                        onCompleteTask = {
                            vm.completeTask { title ->
                                scope.showMessage(snackbar, resources.getString(R.string.snack_completed, title))
                            }
                        },
                        onDismiss = vm::dismissFinished,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.focus_history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 28.dp, bottom = 8.dp),
                )
            }
            val items = history.orEmpty()
            if (history != null && items.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.focus_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
            items(items, key = { it.session.id }) { HistoryRow(it) }
        }
    }

    if (customDialog) {
        TextFieldDialog(
            title = stringResource(R.string.focus_custom_title),
            initial = minutes.toString(),
            label = stringResource(R.string.focus_custom_label),
            maxLength = 3,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            validate = { text ->
                val n = text.trim().toIntOrNull()
                if (n == null || n !in 1..180) resources.getString(R.string.focus_custom_error) else null
            },
            onConfirm = { chosen = it.trim().toInt() },
            onDismiss = { customDialog = false },
        )
    }
}

@Composable
private fun Ring(fraction: Float, time: String, status: String, accessibleTime: String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .padding(top = 12.dp)
            .size(272.dp)
            .clearAndSetSemantics {
                contentDescription = "$status, $accessibleTime"
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    progress, -90f, sweep, useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                time,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Light,
                fontSize = 64.sp,
            )
            Text(status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FinishedCard(session: FinishedSession, onCompleteTask: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    stringResource(R.string.focus_done_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                if (session.taskTitle.isNotEmpty()) stringResource(R.string.focus_done_text_task, session.minutes, session.taskTitle)
                else stringResource(R.string.focus_done_text, session.minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                if (session.taskId != null && !session.taskCompleted) {
                    Button(onClick = onCompleteTask) { Text(stringResource(R.string.focus_complete_task)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: FocusHistoryItem) {
    val context = LocalContext.current
    val s = item.session
    val start = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())
    val focusedMinutes = (s.focusedSeconds / 60).toInt()
    val whenText = "${Formatters.relativeDay(context, start.toLocalDate(), LogicalDay.today())} ${Formatters.time(context, start.toLocalTime())}"
    val detail = if (s.completed) pluralStringResource(R.plurals.focus_history_completed, s.plannedMinutes, s.plannedMinutes)
    else stringResource(R.string.focus_history_stopped, focusedMinutes, s.plannedMinutes)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (s.completed) Icons.Outlined.CheckCircle else Icons.Outlined.Timer,
            contentDescription = null,
            tint = if (s.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(item.taskTitle ?: whenText, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (item.taskTitle != null) "$whenText · $detail" else detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** "24:59", or "1:05:00" for sessions of an hour or more. Rounds up so the display never shows 0:00 early. */
private fun formatRemaining(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1_000
    val h = total / 3_600
    val m = total % 3_600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
