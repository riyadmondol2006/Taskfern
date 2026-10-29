package com.riyadmondol2006.taskfern.update

import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs update checks and holds the release currently offered to the user, so the automatic check on app start and
 * "Check now" in Settings open the same popup. One instance per process (see AppContainer).
 */
class UpdateController(
    private val settings: SettingsRepository,
    private val checker: UpdateChecker,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _offer = MutableStateFlow<ReleaseInfo?>(null)

    /** The release the popup should show, or null. */
    val offer: StateFlow<ReleaseInfo?> = _offer.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val mutex = Mutex()

    /** Set once an automatic check ran in this process, so screen rotations and recreated activities never repeat it. */
    private var autoCheckedThisProcess = false

    /**
     * Checks for an update if the user turned automatic checks on and the last check is more than a day old. Shows the
     * popup only for a newer version the user has not skipped. Failures are silent.
     */
    fun checkAutomaticallyIfDue() {
        scope.launch {
            val s = settings.current()
            val due = mutex.withLock {
                val due = !autoCheckedThisProcess && UpdatePolicy.isCheckDue(now(), s.lastUpdateCheckAt, s.updateCheck)
                if (due) autoCheckedThisProcess = true
                due
            }
            if (!due) return@launch
            val result = run()
            UpdatePolicy.promptFor(result, settings.current().skippedUpdateCode)?.let { _offer.value = it }
        }
    }

    /** "Check now": always asks, and offers a newer version even if it was skipped before. */
    suspend fun checkNow(): UpdateResult {
        val result = run()
        if (result is UpdateResult.Available) _offer.value = result.info
        return result
    }

    /** "Later": hide the popup; it may come back with the next daily check. */
    fun dismiss() {
        _offer.value = null
    }

    /** "Skip this version": hide the popup and do not offer this version (or older ones) automatically again. */
    fun skip(info: ReleaseInfo) {
        _offer.value = null
        scope.launch { settings.setSkippedUpdateCode(info.versionCode) }
    }

    private suspend fun run(): UpdateResult = mutex.withLock {
        _checking.value = true
        try {
            checker.check().also { result ->
                // Remember when GitHub last answered. With no connection at all the next app start tries again.
                if (result !is UpdateResult.Failed || result.reachedServer) settings.setLastUpdateCheckAt(now())
            }
        } finally {
            _checking.value = false
        }
    }
}
