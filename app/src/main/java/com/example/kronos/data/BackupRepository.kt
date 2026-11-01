package com.example.kronos.data

import androidx.room.withTransaction
import com.example.kronos.data.local.*

class BackupRepository(
    private val database: KronosDatabase,
    private val settings: SettingsStore
) {
    suspend fun exportData(): BackupData = BackupData(
        subjects = database.subjectDao().getAll(),
        classes = database.classSessionDao().getAll(),
        categories = database.categoryDao().getAll(),
        tasks = database.taskDao().getAll(),
        exams = database.examDao().getAll(),
        holidays = database.holidayDao().getAll(),
        settings = BackupSettings(
            settings.rotationLength, settings.semesterStart, settings.periods,
            settings.themeMode, settings.pureBlack, settings.timeFormat, settings.firstDayOfWeek,
            settings.defaultReminderOffsetMinutes, settings.taskRemindersEnabled,
            settings.classRemindersEnabled, settings.examRemindersEnabled,
            settings.examDayBeforeEnabled, settings.examHourBeforeEnabled,
            settings.examReminderHour
        )
    )

    suspend fun importData(data: BackupData) {
        database.withTransaction {
            database.classSessionDao().deleteAll()
            database.taskDao().deleteAll()
            database.examDao().deleteAll()
            database.categoryDao().deleteAll()
            database.subjectDao().deleteAll()
            database.holidayDao().deleteAll()

            val subjectIds = data.subjects.associate { it.id to database.subjectDao().insert(it.copy(id = 0)) }
            val categoryIds = data.categories.associate { it.id to database.categoryDao().insert(it.copy(id = 0)) }
            data.classes.forEach {
                database.classSessionDao().insert(it.copy(id = 0, subjectId = BackupIdRemapping.remap(it.subjectId, subjectIds)))
            }
            data.tasks.forEach {
                database.taskDao().insert(it.copy(
                    id = 0,
                    subjectId = it.subjectId?.let { id -> BackupIdRemapping.remap(id, subjectIds) },
                    categoryId = it.categoryId?.let { id -> BackupIdRemapping.remap(id, categoryIds) }
                ))
            }
            data.exams.forEach {
                database.examDao().insert(it.copy(id = 0, subjectId = it.subjectId?.let { id -> BackupIdRemapping.remap(id, subjectIds) }))
            }
            data.holidays.forEach { database.holidayDao().insert(it.copy(id = 0)) }
        }
        settings.rotationLength = data.settings.rotationLength
        settings.semesterStart = data.settings.semesterStart
        settings.periods = data.settings.periods
        settings.themeMode = data.settings.themeMode
        settings.pureBlack = data.settings.pureBlack
        settings.timeFormat = data.settings.timeFormat
        settings.firstDayOfWeek = data.settings.firstDayOfWeek
        settings.defaultReminderOffsetMinutes = data.settings.defaultReminderOffsetMinutes
        settings.taskRemindersEnabled = data.settings.taskRemindersEnabled
        settings.classRemindersEnabled = data.settings.classRemindersEnabled
        settings.examRemindersEnabled = data.settings.examRemindersEnabled
        settings.examDayBeforeEnabled = data.settings.examDayBeforeEnabled
        settings.examHourBeforeEnabled = data.settings.examHourBeforeEnabled
        settings.examReminderHour = data.settings.examReminderHour
    }

}

internal object BackupIdRemapping {
    fun remap(id: Long, ids: Map<Long, Long>): Long =
        ids[id] ?: error("Backup references missing ID $id")
}
