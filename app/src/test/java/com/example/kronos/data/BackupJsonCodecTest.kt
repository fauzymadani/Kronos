package com.example.kronos.data

import com.example.kronos.data.local.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        listOf(Task(6, "Task", "notes", LocalDate.of(2026, 9, 2), LocalTime.NOON, 3, 5, 1, false, LocalDateTime.of(2026, 9, 1, 8, 0))),
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
            BackupJsonCodec.decode(BackupJsonCodec.encode(data).replace("\"formatVersion\":1", "\"formatVersion\":2"))
        }
        assertThrows(BackupValidationException::class.java) {
            BackupJsonCodec.decode(BackupJsonCodec.encode(data).replace("\"databaseSchemaVersion\":8", "\"databaseSchemaVersion\":9"))
        }
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
