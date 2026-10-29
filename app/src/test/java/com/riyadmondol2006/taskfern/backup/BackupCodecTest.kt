package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.backup.BackupFixtures.withoutAlarms
import com.riyadmondol2006.taskfern.data.prefs.Accent
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.TaskSort
import com.riyadmondol2006.taskfern.data.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun decodeOk(text: String): BackupFile {
        val result = BackupCodec.decode(text)
        assertTrue("expected success but was $result", result is BackupReadResult.Success)
        return (result as BackupReadResult.Success).file
    }

    private fun decodeError(text: String): BackupError {
        val result = BackupCodec.decode(text)
        assertTrue("expected failure but was $result", result is BackupReadResult.Failure)
        return (result as BackupReadResult.Failure).error
    }

    @Test
    fun `entities survive export and import unchanged`() {
        val original = BackupFixtures.snapshot()
        val settings = BackupMapper.settingsToBackup(Settings(userName = "Riya", accent = Accent.OCEAN))
        val text = BackupCodec.encode(BackupMapper.toBackup(original, settings, BackupFixtures.T0, "1.2.3"))
        val file = decodeOk(text)
        assertEquals(BackupFile.FORMAT, file.format)
        assertEquals(1, file.version)
        assertEquals("1.2.3", file.appVersion)
        assertEquals(settings, file.settings)
        assertEquals(original.withoutAlarms(), BackupMapper.fromBackup(file))
    }

    @Test
    fun `dates, times and weekdays are written in their documented encodings`() {
        val text = BackupCodec.encode(BackupFixtures.file())
        assertTrue(text.contains("\"format\": \"taskfern-backup\""))
        assertTrue(text.contains("\"dueDate\": \"2026-11-02\""))
        assertTrue(text.contains("\"dueTime\": \"09:30\""))
        assertTrue(text.contains("\"seriesAnchor\": \"2026-10-05\""))
        assertTrue(text.contains("\"time\": \"07:15\""))
        assertTrue(text.contains("\"MONDAY\""))
        assertTrue(text.contains("\"exportedAt\": ${BackupFixtures.T0}"))
        // Alarm bookkeeping is derived and never exported.
        assertTrue(!text.contains("nextReminderAt"))
    }

    @Test
    fun `garbage and empty files are reported as not json`() {
        assertEquals(BackupError.NotJson, decodeError(""))
        assertEquals(BackupError.NotJson, decodeError("   "))
        assertEquals(BackupError.NotJson, decodeError("PK\u0003\u0004 not json at all"))
    }

    @Test
    fun `other json is the wrong format`() {
        assertEquals(BackupError.WrongFormat, decodeError("[1, 2, 3]"))
        assertEquals(BackupError.WrongFormat, decodeError("{\"tasks\": []}"))
        assertEquals(BackupError.WrongFormat, decodeError("{\"format\": \"another-app\", \"version\": 1}"))
    }

    @Test
    fun `a newer format version is refused with its number`() {
        assertEquals(BackupError.NewerVersion(2), decodeError("{\"format\": \"taskfern-backup\", \"version\": 2, \"exportedAt\": 0}"))
    }

    @Test
    fun `missing version or required fields are invalid`() {
        assertTrue(decodeError("{\"format\": \"taskfern-backup\"}") is BackupError.Invalid)
        assertTrue(decodeError("{\"format\": \"taskfern-backup\", \"version\": 1}") is BackupError.Invalid) // no exportedAt
        val noTitle = "{\"format\": \"taskfern-backup\", \"version\": 1, \"exportedAt\": 0, " +
            "\"tasks\": [{\"id\": 1, \"createdAt\": 0, \"updatedAt\": 0}]}"
        assertTrue(decodeError(noTitle) is BackupError.Invalid)
    }

    @Test
    fun `broken references are caught before import`() {
        val file = BackupFixtures.file()
        val broken = file.copy(subtasks = file.subtasks + BackupSubtask(id = 999, taskId = 4242, title = "Orphan"))
        val error = decodeError(BackupCodec.encode(broken))
        assertTrue(error is BackupError.Invalid)
        assertTrue((error as BackupError.Invalid).problems.any { "4242" in it })
    }

    @Test
    fun `unknown fields from the same version are ignored`() {
        val text = BackupCodec.encode(BackupFixtures.file()).replaceFirst("{", "{\n    \"futureField\": {\"x\": 1},")
        decodeOk(text)
    }

    @Test
    fun `a minimal file decodes to an empty backup`() {
        val file = decodeOk("{\"format\": \"taskfern-backup\", \"version\": 1, \"exportedAt\": 5}")
        assertTrue(file.counts.isEmpty)
    }

    @Test
    fun `dangling series links are dropped instead of failing`() {
        val snapshot = BackupFixtures.snapshot()
        val file = BackupFixtures.file(snapshot)
        val withDangling = file.copy(tasks = file.tasks.map { if (it.id == 11L) it.copy(sourceTaskId = 555) else it })
        val decoded = decodeOk(BackupCodec.encode(withDangling))
        assertEquals(null, BackupMapper.fromBackup(decoded).tasks.first { it.id == 11L }.sourceTaskId)
    }

    @Test
    fun `settings restore applies present fields and keeps internal flags`() {
        val current = Settings(onboardingDone = true, templatesSeeded = true, lastQuickAddListId = 9, focusMinutes = 30)
        val backup = BackupSettings(
            themeMode = "DARK", accent = "CLAY", taskSort = "PRIORITY", defaultReminderTime = "07:45",
            dailyAgendaEnabled = true, dailyAgendaTime = "06:00", currencySymbol = " € ",
        )
        val result = BackupMapper.applySettings(current, backup)
        assertEquals(ThemeMode.DARK, result.themeMode)
        assertEquals(Accent.CLAY, result.accent)
        assertEquals(TaskSort.PRIORITY, result.taskSort)
        assertEquals(7 * 60 + 45, result.defaultReminderMinutes)
        assertEquals(true, result.dailyAgendaEnabled)
        assertEquals(6 * 60, result.dailyAgendaMinutes)
        assertEquals("€", result.currencySymbol)
        assertEquals(30, result.focusMinutes) // absent → unchanged
        assertEquals(true, result.onboardingDone)
        assertEquals(true, result.templatesSeeded)
        assertEquals(9L, result.lastQuickAddListId)
    }

    @Test
    fun `exported settings round trip to the same settings`() {
        val s = Settings(
            themeMode = ThemeMode.LIGHT, amoledBlack = true, dynamicColor = false, accent = Accent.DUSK, userName = "Sam",
            defaultReminderMinutes = 8 * 60 + 5, autoReminder = false, dailyAgendaEnabled = true, dailyAgendaMinutes = 6 * 60 + 30,
            weekStartsMonday = false, showCompletedToday = false, taskSort = TaskSort.TITLE, hideCheckedShopping = true,
            showShoppingPrices = true, currencySymbol = "£", focusMinutes = 50, trashRetentionDays = 90,
        )
        assertEquals(s, BackupMapper.applySettings(Settings(), BackupMapper.settingsToBackup(s)))
    }
}
