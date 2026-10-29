package com.riyadmondol2006.taskfern.data.repo

import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.db.TemplateKind

/**
 * Editable templates that create a task list, a shopping list or a routine in one tap. The built-in set is added once on
 * first launch; after that they belong to the user (edit or delete freely).
 */
class TemplateRepository(
    private val db: TaskfernDatabase,
    private val tasks: TaskRepository,
    private val shopping: ShoppingRepository,
    private val routines: RoutineRepository,
) {
    private val dao = db.templateDao()

    fun observeAll() = dao.observeAll()
    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(template: TemplateEntity): Long =
        if (template.id == 0L) dao.insert(template) else template.id.also { dao.update(template) }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun seedBuiltins() {
        if (dao.getAll().isNotEmpty()) return
        dao.insertAll(BUILTINS)
    }

    /** Creates something from a template and returns the new object's id. */
    suspend fun apply(template: TemplateEntity): Long = when (template.kind) {
        TemplateKind.SHOPPING -> {
            val listId = shopping.createList(template.name)
            shopping.addAll(listId, template.itemLines)
            listId
        }
        TemplateKind.ROUTINE -> routines.create(
            RoutineDraft(
                name = template.name, emoji = template.emoji.ifEmpty { "🌿" },
                steps = template.itemLines.map { StepDraft(title = it) },
            ),
        )
        else -> {
            val listId = tasks.createList(template.name)
            template.itemLines.asReversed().forEach { tasks.create(TaskDraft(title = it, listId = listId)) }
            listId
        }
    }

    companion object {
        val BUILTINS = listOf(
            TemplateEntity(
                kind = TemplateKind.SHOPPING, name = "Weekly groceries", emoji = "🛒", builtin = true, position = 0,
                items = "Milk\nEggs\nBread\nBananas\nApples\nTomatoes\nOnions\nRice\nCheese\nCoffee",
            ),
            TemplateEntity(
                kind = TemplateKind.TASKS, name = "Packing", emoji = "🧳", builtin = true, position = 1,
                items = "Passport / ID\nPhone charger\nToothbrush & toothpaste\nMedication\nClothes for each day\n" +
                    "Sleepwear\nRain jacket\nHeadphones\nTravel documents\nSnacks for the journey",
            ),
            TemplateEntity(
                kind = TemplateKind.TASKS, name = "House cleaning", emoji = "🧽", builtin = true, position = 2,
                items = "Dust surfaces\nVacuum floors\nMop kitchen and bathroom\nClean bathroom sink and mirror\n" +
                    "Change bed sheets\nTake out recycling\nWipe kitchen counters\nWater plants",
            ),
            TemplateEntity(
                kind = TemplateKind.TASKS, name = "Weekly planning", emoji = "🗓️", builtin = true, position = 3,
                items = "Review last week\nCheck calendar for the coming week\nPick three priorities\n" +
                    "Plan meals\nSchedule exercise\nClear the inbox list",
            ),
            TemplateEntity(
                kind = TemplateKind.TASKS, name = "Study schedule", emoji = "📚", builtin = true, position = 4,
                items = "Review lecture notes\nRead the next chapter\nDo practice problems\nMake flashcards\n" +
                    "Revise flashcards\nPrepare questions for class",
            ),
            TemplateEntity(
                kind = TemplateKind.ROUTINE, name = "Morning routine", emoji = "☀️", builtin = true, position = 5,
                items = "Drink a glass of water\nStretch for 5 minutes\nMake the bed\nReview today's tasks",
            ),
            TemplateEntity(
                kind = TemplateKind.ROUTINE, name = "Evening wind-down", emoji = "🌙", builtin = true, position = 6,
                items = "Tidy up for 10 minutes\nPrepare tomorrow\nRead\nScreens off",
            ),
        )
    }
}
