package com.riyadmondol2006.taskfern.backup

import android.content.Context
import android.net.Uri
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.BuildConfig
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/** Thrown when the chosen file cannot be opened or written. */
class BackupIoException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Export and import through the Storage Access Framework (the user picks the file; no storage permission needed),
 * plus the follow-up work after an import: settings, alarms, widgets and daily jobs.
 */
class BackupService(private val context: Context, private val container: AppContainer) {
    private val store get() = BackupStore(container.database)

    suspend fun currentCounts(): BackupCounts = store.snapshot().counts

    /** Writes a full backup to [uri] and returns what it contains. */
    suspend fun export(uri: Uri): BackupCounts = withContext(Dispatchers.IO) {
        val snapshot = store.snapshot()
        val settings = BackupMapper.settingsToBackup(container.settings.current())
        val file = BackupMapper.toBackup(snapshot, settings, System.currentTimeMillis(), BuildConfig.VERSION_NAME)
        val bytes = BackupCodec.encode(file).toByteArray(Charsets.UTF_8)
        openOutput(uri).use { it.write(bytes) }
        snapshot.counts
    }

    /** Writes open dated tasks to [uri] as an iCalendar file and returns how many events it has. */
    suspend fun exportCalendar(uri: Uri): Int = withContext(Dispatchers.IO) {
        val tasks = store.snapshot().tasks
        val text = IcsExporter.export(tasks, Instant.now(), BuildConfig.VERSION_NAME)
        openOutput(uri).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        IcsExporter.exportable(tasks).size
    }

    /** Reads and validates a backup. Never throws for bad content; the failure says what is wrong. */
    suspend fun read(uri: Uri): BackupReadResult = withContext(Dispatchers.IO) {
        val bytes = try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupReadResult.Failure(BackupError.Unreadable("The file could not be opened."))
            input.use { readLimited(it) }
        } catch (e: TooLargeException) {
            return@withContext BackupReadResult.Failure(BackupError.Unreadable(e.message.orEmpty()))
        } catch (e: IOException) {
            return@withContext BackupReadResult.Failure(BackupError.Unreadable(e.message ?: e.javaClass.simpleName))
        } catch (e: SecurityException) {
            return@withContext BackupReadResult.Failure(BackupError.Unreadable(e.message ?: e.javaClass.simpleName))
        }
        BackupCodec.decode(bytes.toString(Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK))
    }

    /** Adds the backup to the existing data. See [MergePlanner] for how duplicates are recognised. */
    suspend fun merge(file: BackupFile, restoreSettings: Boolean): MergePlan {
        val plan = withContext(Dispatchers.IO) { store.merge(BackupMapper.fromBackup(file)) }
        afterImport(file.settings.takeIf { restoreSettings })
        return plan
    }

    /** Deletes all current data and restores the backup exactly, with its original ids. */
    suspend fun replace(file: BackupFile, restoreSettings: Boolean): BackupCounts {
        val snapshot = BackupMapper.fromBackup(file)
        withContext(Dispatchers.IO) { store.replace(snapshot) }
        afterImport(file.settings.takeIf { restoreSettings })
        return snapshot.counts
    }

    private suspend fun afterImport(settings: BackupSettings?) {
        if (settings != null) restoreSettings(container.settings, settings)
        container.reminders.rescheduleAll(catchUp = false)
        container.widgets.refreshAll()
        container.scheduleDailyWork()
    }

    private fun openOutput(uri: Uri): OutputStream {
        val resolver = context.contentResolver
        // "wt" truncates an existing file; a few providers only know "w".
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } catch (_: java.io.FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        }
        return stream ?: throw BackupIoException("The file could not be opened for writing.")
    }

    private class TooLargeException(message: String) : IOException(message)

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw TooLargeException("The file is larger than ${MAX_BYTES / (1024 * 1024)} MB.")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        /** Far above any real backup; protects against picking a video by mistake. */
        const val MAX_BYTES = 64L * 1024 * 1024

        /** Some editors start UTF-8 files with U+FEFF; JSON parsers reject it. */
        private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

        fun fileName(date: java.time.LocalDate): String = "taskfern-backup-$date.json"

        /** Applies every setting present in the backup through the regular setters (which also clamp values). */
        suspend fun restoreSettings(repo: SettingsRepository, backup: BackupSettings) {
            val current: Settings = repo.current()
            val s = BackupMapper.applySettings(current, backup)
            if (s.themeMode != current.themeMode) repo.setThemeMode(s.themeMode)
            if (s.amoledBlack != current.amoledBlack) repo.setAmoledBlack(s.amoledBlack)
            if (s.dynamicColor != current.dynamicColor) repo.setDynamicColor(s.dynamicColor)
            if (s.accent != current.accent) repo.setAccent(s.accent)
            if (s.userName != current.userName) repo.setUserName(s.userName)
            if (s.defaultReminderMinutes != current.defaultReminderMinutes) repo.setDefaultReminderMinutes(s.defaultReminderMinutes)
            if (s.autoReminder != current.autoReminder) repo.setAutoReminder(s.autoReminder)
            if (s.dailyAgendaEnabled != current.dailyAgendaEnabled) repo.setDailyAgendaEnabled(s.dailyAgendaEnabled)
            if (s.dailyAgendaMinutes != current.dailyAgendaMinutes) repo.setDailyAgendaMinutes(s.dailyAgendaMinutes)
            if (s.weekStartsMonday != current.weekStartsMonday) repo.setWeekStartsMonday(s.weekStartsMonday)
            if (s.showCompletedToday != current.showCompletedToday) repo.setShowCompletedToday(s.showCompletedToday)
            if (s.taskSort != current.taskSort) repo.setTaskSort(s.taskSort)
            if (s.hideCheckedShopping != current.hideCheckedShopping) repo.setHideCheckedShopping(s.hideCheckedShopping)
            if (s.showShoppingPrices != current.showShoppingPrices) repo.setShowShoppingPrices(s.showShoppingPrices)
            if (s.currencySymbol != current.currencySymbol) repo.setCurrencySymbol(s.currencySymbol)
            if (s.focusMinutes != current.focusMinutes) repo.setFocusMinutes(s.focusMinutes)
            if (s.trashRetentionDays != current.trashRetentionDays) repo.setTrashRetentionDays(s.trashRetentionDays)
        }
    }
}
