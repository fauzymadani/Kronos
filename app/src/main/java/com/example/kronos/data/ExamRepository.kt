package com.example.kronos.data

import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.ExamDao
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.SubjectDao
import kotlinx.coroutines.flow.Flow

class ExamRepository(
    private val exams: ExamDao,
    private val subjects: SubjectDao
) {
    val examFlow: Flow<List<ExamWithSubject>> = exams.observeAll()
    val subjectFlow: Flow<List<Subject>> = subjects.observeAll()

    suspend fun insert(exam: Exam) = exams.insert(exam)
    suspend fun update(exam: Exam) = exams.update(exam)
    suspend fun delete(exam: Exam) = exams.delete(exam)
    suspend fun find(id: Long): Exam? = exams.findById(id)
    suspend fun getSubjects(): List<Subject> = subjects.getAll()
}
