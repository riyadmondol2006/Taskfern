package com.riyadmondol2006.taskfern.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ShoppingListWithCounts(
    @Embedded val list: ShoppingListEntity,
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "remaining") val remaining: Int,
)

@Dao
interface ShoppingDao {
    @Query(
        """SELECT l.*, (SELECT COUNT(*) FROM shopping_items i WHERE i.list_id = l.id) AS total,
        (SELECT COUNT(*) FROM shopping_items i WHERE i.list_id = l.id AND i.checked = 0) AS remaining
        FROM shopping_lists l ORDER BY l.position, l.id""",
    )
    fun observeListsWithCounts(): Flow<List<ShoppingListWithCounts>>

    @Query("SELECT * FROM shopping_lists ORDER BY position, id")
    suspend fun getLists(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun observeList(id: Long): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getList(id: Long): ShoppingListEntity?

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId ORDER BY checked, position, id")
    fun observeItems(listId: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId ORDER BY checked, position, id")
    suspend fun getItems(listId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId AND checked = 0 ORDER BY position, id LIMIT :limit")
    suspend fun getUncheckedItems(listId: Long, limit: Int): List<ShoppingItemEntity>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE list_id = :listId AND checked = 0")
    suspend fun remainingCount(listId: Long): Int

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getItem(id: Long): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE list_id = :listId AND lower(trim(name)) = :nameKey AND checked = 0 LIMIT 1")
    suspend fun findUncheckedByName(listId: Long, nameKey: String): ShoppingItemEntity?

    @Insert
    suspend fun insertList(list: ShoppingListEntity): Long

    @Update
    suspend fun updateList(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM shopping_lists")
    suspend fun nextListPosition(): Int

    @Insert
    suspend fun insertItem(item: ShoppingItemEntity): Long

    @Insert
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Update
    suspend fun updateItems(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE shopping_items SET checked = :checked, checked_at = :at WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean, at: Long?)

    @Query("DELETE FROM shopping_items WHERE list_id = :listId AND checked = 1")
    suspend fun clearChecked(listId: Long): Int

    @Query("UPDATE shopping_items SET checked = 0, checked_at = NULL WHERE list_id = :listId")
    suspend fun uncheckAll(listId: Long)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM shopping_items WHERE list_id = :listId")
    suspend fun nextItemPosition(listId: Long): Int

    // Item memory (suggestions)
    @Query("SELECT * FROM item_memory WHERE name_key = :key")
    suspend fun getMemory(key: String): ItemMemoryEntity?

    @Upsert
    suspend fun upsertMemory(memory: ItemMemoryEntity)

    @Query("SELECT * FROM item_memory WHERE name_key LIKE :prefix || '%' ESCAPE '\\' ORDER BY use_count DESC, last_used_at DESC LIMIT :limit")
    suspend fun suggestions(prefix: String, limit: Int): List<ItemMemoryEntity>

    @Query("SELECT * FROM item_memory ORDER BY use_count DESC, last_used_at DESC LIMIT :limit")
    fun observeFrequent(limit: Int): Flow<List<ItemMemoryEntity>>

    @Query("SELECT * FROM item_memory ORDER BY last_used_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ItemMemoryEntity>>

    @Query("DELETE FROM item_memory WHERE name_key = :key")
    suspend fun forgetMemory(key: String)

    @Query("SELECT * FROM item_memory")
    suspend fun allMemory(): List<ItemMemoryEntity>

    @Query("SELECT * FROM shopping_items")
    suspend fun allItems(): List<ShoppingItemEntity>
}

data class RoutineStepWithCheck(
    @Embedded val step: RoutineStepEntity,
    @ColumnInfo(name = "checked") val checked: Boolean,
)

data class RoutineDayProgress(
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val date: LocalDate,
    val done: Int,
)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY position, id")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY position, id")
    suspend fun getRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeRoutine(id: Long): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: Long): RoutineEntity?

    @Query("SELECT * FROM routine_steps ORDER BY routine_id, position, id")
    fun observeAllSteps(): Flow<List<RoutineStepEntity>>

    @Query("SELECT * FROM routine_steps WHERE routine_id = :routineId ORDER BY position, id")
    suspend fun getSteps(routineId: Long): List<RoutineStepEntity>

    @Query(
        """SELECT s.*, EXISTS(SELECT 1 FROM routine_checks c WHERE c.step_id = s.id AND c.date = :date) AS checked
        FROM routine_steps s WHERE s.routine_id = :routineId ORDER BY s.position, s.id""",
    )
    fun observeStepsWithChecks(routineId: Long, date: LocalDate): Flow<List<RoutineStepWithCheck>>

    @Query(
        """SELECT s.*, EXISTS(SELECT 1 FROM routine_checks c WHERE c.step_id = s.id AND c.date = :date) AS checked
        FROM routine_steps s WHERE s.routine_id = :routineId ORDER BY s.position, s.id""",
    )
    suspend fun getStepsWithChecks(routineId: Long, date: LocalDate): List<RoutineStepWithCheck>

    @Query("SELECT * FROM routine_checks WHERE date = :date")
    fun observeChecksOn(date: LocalDate): Flow<List<RoutineCheckEntity>>

    @Query("SELECT routine_id, date, COUNT(*) AS done FROM routine_checks WHERE date BETWEEN :from AND :to GROUP BY routine_id, date")
    fun observeProgressBetween(from: LocalDate, to: LocalDate): Flow<List<RoutineDayProgress>>

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM routines")
    suspend fun nextRoutinePosition(): Int

    @Insert
    suspend fun insertStep(step: RoutineStepEntity): Long

    @Insert
    suspend fun insertSteps(steps: List<RoutineStepEntity>)

    @Update
    suspend fun updateSteps(steps: List<RoutineStepEntity>)

    @Query("DELETE FROM routine_steps WHERE id = :id")
    suspend fun deleteStep(id: Long)

    @Query("DELETE FROM routine_steps WHERE routine_id = :routineId AND id NOT IN (:keep)")
    suspend fun deleteStepsExcept(routineId: Long, keep: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheck(check: RoutineCheckEntity)

    @Query("DELETE FROM routine_checks WHERE step_id = :stepId AND date = :date")
    suspend fun deleteCheck(stepId: Long, date: LocalDate)

    @Query("DELETE FROM routine_checks WHERE routine_id = :routineId AND date = :date")
    suspend fun clearChecks(routineId: Long, date: LocalDate)

    @Query("SELECT EXISTS(SELECT 1 FROM routine_checks WHERE step_id = :stepId AND date = :date)")
    suspend fun isChecked(stepId: Long, date: LocalDate): Boolean

    @Query("UPDATE routines SET next_reminder_at = :at WHERE id = :id")
    suspend fun setNextReminderAt(id: Long, at: Long?)

    @Query("SELECT * FROM routine_checks")
    suspend fun allChecks(): List<RoutineCheckEntity>

    @Query("SELECT * FROM routine_steps")
    suspend fun allSteps(): List<RoutineStepEntity>
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY kind, position, id")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY kind, position, id")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): TemplateEntity?

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Insert
    suspend fun insertAll(templates: List<TemplateEntity>)

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FocusDao {
    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun get(id: Long): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(focused_seconds), 0) FROM focus_sessions WHERE started_at >= :since")
    fun observeFocusedSecondsSince(since: Long): Flow<Long>

    @Query("SELECT * FROM focus_sessions")
    suspend fun all(): List<FocusSessionEntity>
}

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configs WHERE app_widget_id = :id")
    suspend fun get(id: Int): WidgetConfigEntity?

    @Query("SELECT * FROM widget_configs WHERE app_widget_id = :id")
    fun observe(id: Int): Flow<WidgetConfigEntity?>

    @Upsert
    suspend fun upsert(config: WidgetConfigEntity)

    @Query("DELETE FROM widget_configs WHERE app_widget_id IN (:ids)")
    suspend fun delete(ids: List<Int>)

    @Query("SELECT * FROM widget_configs")
    suspend fun all(): List<WidgetConfigEntity>
}
