package com.example.kronos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Subject::class,
        ClassSession::class,
        Task::class,
        Category::class,
        Exam::class,
        Holiday::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KronosDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun classSessionDao(): ClassSessionDao
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
}
