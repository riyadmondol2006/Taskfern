package com.riyadmondol2006.taskfern.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the exported schemas in app/schemas. Every released schema version must open and migrate to the current one
 * with the migrations in [TaskfernDatabase.MIGRATIONS]; when a new version is added, extend [migrateAll] with a
 * data-carrying assertion for the new migration.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskfernDatabase::class.java,
    )

    @Test
    fun currentSchemaMatchesExportedSchema() {
        helper.createDatabase(dbName, TaskfernDatabase.VERSION).apply {
            execSQL("INSERT INTO task_lists (id, name, color, icon, position, favorite, created_at) VALUES (1, 'Home', 0, 'list', 0, 0, 0)")
            execSQL("INSERT INTO tasks (id, list_id, title, notes, priority, tags, pinned, archived, is_completed, created_at, updated_at, position, occurrence_count) VALUES (1, 1, 'Kept', '', 0, '', 0, 0, 0, 0, 0, 0, 0)")
            close()
        }
        // Validates that Room's expected schema equals what the helper created from the JSON.
        val db = helper.runMigrationsAndValidate(dbName, TaskfernDatabase.VERSION, true, *TaskfernDatabase.MIGRATIONS)
        db.query("SELECT title FROM tasks WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Kept", c.getString(0))
        }
        db.close()
    }

    @Test
    fun migrateAll() {
        // Version 1 is the first released schema; there is nothing to migrate from yet. Opening the oldest exported
        // version and migrating to the newest must keep user rows.
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO shopping_lists (id, name, color, position, created_at) VALUES (1, 'Groceries', 0, 0, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, TaskfernDatabase.VERSION, true, *TaskfernDatabase.MIGRATIONS)
        db.query("SELECT COUNT(*) FROM shopping_lists").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }
}
