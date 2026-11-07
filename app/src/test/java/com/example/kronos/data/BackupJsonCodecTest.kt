package com.example.kronos.data

import com.example.kronos.data.local.*
import com.example.kronos.lib.AssignmentType
import com.example.kronos.lib.TaskStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BackupJsonCodecTest {
    private val settings = BackupSettings(
        2, LocalDate.of(2026, 9, 1),
        listOf(ClassPeriod("1", LocalTime.of(8, 0), LocalTime.of(8, 50))),
        ThemeMode.DARK, true, TimeFormat.HOUR_12, FirstDayOfWeek.SUNDAY,
        15, true, false, true, false, true, LocalTime.of(9, 0)
    )

    private val data = BackupData(
        listOf(Subject(3, "Math", "M", "#123456")),
        listOf(ClassSession(4, 3, 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "A,1", "T", 2, "note", true, 5)),
        listOf(Category(5, "Work", "#fff")),
        listOf(Task(6, "Task", "notes", LocalDate.of(2026, 9, 2), LocalTime.NOON, 3, 5, 1, true, LocalDateTime.of(2026, 9, 1, 8, 0), TaskStatus.DONE.code, AssignmentType.GROUP.code, 1_700_000_000_000L)),
        listOf(Exam(7, "Exam", 3, LocalDate.of(2026, 9, 3), LocalTime.of(12, 0), "Room", "1", "notes")),
        listOf(Holiday(8, "Break", LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 5), null)),
        settings
    )

    @Test
    fun roundTripPreservesAllData() {
        assertEquals(data, BackupJsonCodec.decode(BackupJsonCodec.encode(data)))
    }

    @Test
    fun rejectsNewerFormatAndSchema() {
        assertThrows(BackupValidationException::class.java) {
            BackupJsonCodec.decode(BackupJsonCodec.encode(data).replace("\"formatVersion\":2", "\"formatVersion\":3"))
        }
        assertThrows(BackupValidationException::class.java) {
            BackupJsonCodec.decode(BackupJsonCodec.encode(data).replace("\"databaseSchemaVersion\":9", "\"databaseSchemaVersion\":10"))
        }
    }

    @Test
    fun importsOldFormatTaskFromDoneFlag() {
        val done = BackupJsonCodec.decode(withoutStatusKeys(done = true)).tasks.single()
        assertEquals(TaskStatus.DONE, TaskStatus.fromCode(done.status))
        assertTrue(done.done)
        assertEquals(AssignmentType.INDIVIDUAL, AssignmentType.fromCode(done.assignmentType))
        assertNull(done.completedAt)

        val open = BackupJsonCodec.decode(withoutStatusKeys(done = false)).tasks.single()
        assertEquals(TaskStatus.NOT_STARTED, TaskStatus.fromCode(open.status))
        assertFalse(open.done)
        assertNull(open.completedAt)
    }

    @Test
    fun unknownStatusCodeFallsBackSafely() {
        val task = BackupJsonCodec.decode(
            BackupJsonCodec.encode(data).replace("\"status\":4", "\"status\":99")
        ).tasks.single()
        assertEquals(TaskStatus.NOT_STARTED, TaskStatus.fromCode(task.status))
        assertFalse(task.done)
    }

    /** Old backups have formatVersion 1 and no task status/assignmentType/completedAt keys. */
    private fun withoutStatusKeys(done: Boolean): String {
        val root = JSONObject(BackupJsonCodec.encode(data))
        root.put("formatVersion", 1)
        val tasks = root.getJSONArray("tasks")
        for (i in 0 until tasks.length()) {
            tasks.getJSONObject(i).apply {
                put("done", done)
                remove("status")
                remove("assignmentType")
                remove("completedAt")
            }
        }
        return root.toString()
    }

    @Test
    fun rejectsInvalidFile() {
        assertThrows(BackupValidationException::class.java) {
            BackupJsonCodec.decode("{}")
        }
    }

    @Test
    fun remapsForeignKeysConsistently() {
        val subjects = mapOf(3L to 30L)
        val categories = mapOf(5L to 50L)
        assertEquals(30L, BackupIdRemapping.remap(data.classes.single().subjectId, subjects))
        assertEquals(30L, BackupIdRemapping.remap(data.tasks.single().subjectId!!, subjects))
        assertEquals(50L, BackupIdRemapping.remap(data.tasks.single().categoryId!!, categories))
    }
}
