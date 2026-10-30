package com.example.kronos.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ClassSessionWithSubject(
    val id: Long,
    val subjectId: Long,
    val subjectName: String,
    val subjectColor: String?,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val room: String?,
    val teacher: String?,
    val weekNumber: Int,
    val notes: String?
)

@Dao
interface SubjectDao {
    @Insert
    suspend fun insert(subject: Subject): Long

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun findById(id: Long): Subject?

    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Subject?

    @Update
    suspend fun update(subject: Subject)
}

@Dao
interface ClassSessionDao {
    @Query(
        """
        SELECT c.id, c.subjectId, s.name AS subjectName, s.color AS subjectColor,
               c.dayOfWeek, c.startTime, c.endTime, c.room, c.teacher,
               c.weekNumber, c.notes
        FROM class_sessions c
        INNER JOIN subjects s ON s.id = c.subjectId
        WHERE c.dayOfWeek = :dayOfWeek
          AND (c.weekNumber = 0 OR c.weekNumber = :weekNumber)
        ORDER BY c.startTime ASC
        """
    )
    fun observeForDay(dayOfWeek: Int, weekNumber: Int): Flow<List<ClassSessionWithSubject>>

    @Insert
    suspend fun insert(session: ClassSession): Long

    @Update
    suspend fun update(session: ClassSession)

    @Query("SELECT * FROM class_sessions WHERE id = :id")
    suspend fun findById(id: Long): ClassSession?

    @Delete
    suspend fun delete(session: ClassSession)
}
