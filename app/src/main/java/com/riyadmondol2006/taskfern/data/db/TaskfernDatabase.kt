package com.riyadmondol2006.taskfern.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * The single on-device database.
 *
 * Schema changes must ship a [Migration] in [MIGRATIONS] (or an auto-migration) and bump [VERSION]; the exported schema
 * JSON in app/schemas is checked by MigrationTest. There is deliberately no destructive fallback: an upgrade must never
 * silently erase someone's tasks. If a migration is missing the app fails loudly in testing instead.
 */
@Database(
    entities = [
        TaskListEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        ReminderEntity::class,
        OccurrenceLogEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        ItemMemoryEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        RoutineCheckEntity::class,
        TemplateEntity::class,
        FocusSessionEntity::class,
        WidgetConfigEntity::class,
    ],
    version = TaskfernDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskfernDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun routineDao(): RoutineDao
    abstract fun templateDao(): TemplateDao
    abstract fun focusDao(): FocusDao
    abstract fun widgetConfigDao(): WidgetConfigDao

    companion object {
        const val VERSION = 1
        const val NAME = "taskfern.db"

        /** Every migration ever shipped, oldest first. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context, inMemory: Boolean = false): TaskfernDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, TaskfernDatabase::class.java)
            } else {
                Room.databaseBuilder(context, TaskfernDatabase::class.java, NAME)
            }
            return builder
                .addMigrations(*MIGRATIONS)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
