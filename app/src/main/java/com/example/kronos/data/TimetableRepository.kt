package com.example.kronos.data

import com.example.kronos.data.local.ClassSession
import com.example.kronos.data.local.ClassSessionDao
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.SubjectDao
import com.example.kronos.data.local.Holiday
import com.example.kronos.data.local.HolidayDao
import kotlinx.coroutines.flow.Flow
import com.example.kronos.lib.normalizeName

class TimetableRepository(
    private val sessions: ClassSessionDao,
    private val subjects: SubjectDao,
    private val holidays: HolidayDao
) {
    fun observeHolidays(): Flow<List<Holiday>> = holidays.observeAll()
    suspend fun saveHoliday(holiday: Holiday) {
        if (holiday.id == 0L) holidays.insert(holiday) else holidays.update(holiday)
    }
    suspend fun deleteHoliday(holiday: Holiday) = holidays.delete(holiday)
    suspend fun findHoliday(id: Long): Holiday? = holidays.findById(id)
    fun observeForDay(dayOfWeek: Int, weekNumber: Int): Flow<List<ClassSessionWithSubject>> =
        sessions.observeForDay(dayOfWeek, weekNumber)

    fun observeForWeek(weekNumber: Int): Flow<List<ClassSessionWithSubject>> =
        sessions.observeForWeek(weekNumber)

    suspend fun save(
        existing: ClassSession?,
        subjectName: String,
        color: String,
        dayOfWeek: Int,
        startTime: java.time.LocalTime,
        endTime: java.time.LocalTime,
        room: String?,
        teacher: String?,
        notes: String?,
        weekNumber: Int,
        reminderEnabled: Boolean,
        reminderOffsetMinutes: Int?
    ) {
        val trimmedName = subjectName.trim()
        require(trimmedName.isNotEmpty())
        val subject = subjects.findByNormalizedName(normalizeName(trimmedName))
        val subjectId = if (subject == null) {
            subjects.insert(Subject(name = trimmedName, color = color))
        } else {
            subjects.update(subject.copy(color = color))
            subject.id
        }
        val session = ClassSession(
            id = existing?.id ?: 0,
            subjectId = subjectId,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            room = room?.trim()?.takeIf(String::isNotEmpty),
            teacher = teacher?.trim()?.takeIf(String::isNotEmpty),
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            weekNumber = weekNumber,
            reminderEnabled = reminderEnabled,
            reminderOffsetMinutes = reminderOffsetMinutes
        )
        if (existing == null) sessions.insert(session) else sessions.update(session)
    }

    suspend fun delete(session: ClassSession) = sessions.delete(session)

    suspend fun findSession(id: Long): ClassSession? = sessions.findById(id)
}
