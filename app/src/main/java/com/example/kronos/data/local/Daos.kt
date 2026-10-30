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

    @Query("SELECT * FROM subjects ORDER BY name")
    fun observeAll(): Flow<List<Subject>>
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

    @Query(
        """
        SELECT c.id, c.subjectId, s.name AS subjectName, s.color AS subjectColor,
               c.dayOfWeek, c.startTime, c.endTime, c.room, c.teacher,
               c.weekNumber, c.notes
        FROM class_sessions c
        INNER JOIN subjects s ON s.id = c.subjectId
        WHERE c.weekNumber = 0 OR c.weekNumber = :weekNumber
        ORDER BY c.dayOfWeek ASC, c.startTime ASC
        """
    )
    fun observeForWeek(weekNumber: Int): Flow<List<ClassSessionWithSubject>>

    @Insert
    suspend fun insert(session: ClassSession): Long

    @Update
    suspend fun update(session: ClassSession)

    @Query("SELECT * FROM class_sessions WHERE id = :id")
    suspend fun findById(id: Long): ClassSession?

    @Delete
    suspend fun delete(session: ClassSession)
}

data class TaskWithSubject(
    val id: Long,
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val dueTime: String?,
    val subjectId: Long?,
    val subjectName: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColor: String?,
    val priority: Int,
    val done: Boolean
)

@Dao
interface TaskDao {
    @Query(
        """
        SELECT t.id, t.title, t.notes, t.dueDate, t.dueTime, t.subjectId,
               s.name AS subjectName, t.categoryId, c.name AS categoryName,
               c.color AS categoryColor, t.priority, t.done
        FROM tasks t
        LEFT JOIN subjects s ON s.id = t.subjectId
        LEFT JOIN categories c ON c.id = t.categoryId
        ORDER BY t.done ASC, t.dueDate IS NULL, t.dueDate ASC, t.dueTime ASC, t.priority DESC
        """
    )
    fun observeAll(): Flow<List<TaskWithSubject>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): Task?

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<Category>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}
