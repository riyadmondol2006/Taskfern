package com.riyadmondol2006.taskfern.update

/** Outcome of one update check. */
sealed interface UpdateResult {
    /** The newest stable release is not newer than this app. */
    data object UpToDate : UpdateResult

    data class Available(val info: ReleaseInfo) : UpdateResult

    /** GitHub answered 404: the repository has no published release yet. */
    data object NoReleaseYet : UpdateResult

    /**
     * The check did not work. [reachedServer] is true when GitHub answered (an error status or an unusable response)
     * and false when there was no connection at all.
     */
    data class Failed(val reason: String, val reachedServer: Boolean) : UpdateResult
}

/** Pure decisions of the update check, kept apart from the network and storage so they are easy to test. */
object UpdatePolicy {
    /** Automatic checks run at most once per this interval. */
    const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

    fun isNewer(releaseCode: Int, installedCode: Int): Boolean = releaseCode > installedCode

    /** Compares a parsed "latest release" with the installed version. */
    fun evaluate(parsed: ParsedRelease, installedCode: Int): UpdateResult = when (parsed) {
        is ParsedRelease.Stable ->
            if (isNewer(parsed.info.versionCode, installedCode)) UpdateResult.Available(parsed.info) else UpdateResult.UpToDate
        ParsedRelease.NotStable -> UpdateResult.UpToDate
        is ParsedRelease.Malformed -> UpdateResult.Failed(parsed.reason, reachedServer = true)
    }

    /**
     * Whether an automatic check should run now: only when the user turned it on ([enabled] is null until they answer)
     * and the last answered check is at least [CHECK_INTERVAL_MILLIS] old. A [lastCheckAt] in the future means the
     * clock was set back; the check is then due rather than postponed for that long.
     */
    fun isCheckDue(now: Long, lastCheckAt: Long, enabled: Boolean?): Boolean = when {
        enabled != true -> false
        lastCheckAt <= 0L -> true
        now < lastCheckAt -> true
        else -> now - lastCheckAt >= CHECK_INTERVAL_MILLIS
    }

    /** The release to show in the automatic popup, or null when there is none or the user skipped this version. */
    fun promptFor(result: UpdateResult, skippedCode: Int): ReleaseInfo? =
        (result as? UpdateResult.Available)?.info?.takeIf { it.versionCode > skippedCode }
}
