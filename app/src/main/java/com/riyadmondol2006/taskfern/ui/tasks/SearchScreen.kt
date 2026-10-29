package com.riyadmondol2006.taskfern.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.SectionHeader
import com.riyadmondol2006.taskfern.ui.common.TaskItem
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(tasks: TaskRepository) : TaskActionsViewModel(tasks) {
    val query = MutableStateFlow("")

    val results: StateFlow<List<TaskRow>?> = query.debounce(150).flatMapLatest { q ->
        if (q.isBlank()) flowOf(null) else tasks.search(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentTitles: StateFlow<List<String>> = tasks.observeRecentTitles(12).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags: StateFlow<List<String>> = tasks.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Local full-text-ish search over titles, notes, tags and checklist steps. */
@Composable
fun SearchScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> SearchViewModel(c.tasks) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recentTitles.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    TaskEventsEffect(vm)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        val list = results
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            if (list == null) {
                if (tags.isNotEmpty()) {
                    item(key = "tags") {
                        Column {
                            SectionHeader(stringResource(R.string.tasks_tags))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tags.forEach { t -> AssistChip(onClick = { nav.openTaskList(SmartViewKey.TAG_PREFIX + t) }, label = { Text("#$t") }) }
                            }
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        Column {
                            SectionHeader(stringResource(R.string.search_recent_titles))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recent.forEach { t -> AssistChip(onClick = { vm.query.value = t }, label = { Text(t, maxLines = 1) }) }
                            }
                        }
                    }
                }
            } else if (list.isEmpty()) {
                item(key = "none") {
                    EmptyState(
                        title = stringResource(R.string.search_no_results, query.trim()),
                        message = stringResource(R.string.search_try_other),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                items(list, key = { it.task.id }) { row ->
                    TaskItem(
                        row,
                        onToggle = { vm.toggle(row) },
                        onClick = { nav.openTask(row.task.id) },
                        streak = streaks[row.task.id]?.current ?: 0,
                    )
                }
            }
        }
    }
}
