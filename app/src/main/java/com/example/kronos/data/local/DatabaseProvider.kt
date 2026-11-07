package com.example.kronos.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {
    @Volatile
    private var instance: KronosDatabase? = null

    fun get(context: Context): KronosDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KronosDatabase::class.java,
                "kronos.db"
            ).addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
            ).build().also { instance = it }
        }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE class_sessions ADD COLUMN weekNumber INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN dueTime TEXT")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exams ADD COLUMN seat TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE holidays_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    endDate TEXT NOT NULL,
                    notes TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO holidays_new (id, name, startDate, endDate, notes)
                SELECT id, name, date, COALESCE(endDate, date), notes FROM holidays
                """.trimIndent()
            )
            db.execSQL("DROP TABLE holidays")
            db.execSQL("ALTER TABLE holidays_new RENAME TO holidays")
            db.execSQL("CREATE INDEX index_holidays_startDate ON holidays(startDate)")
            db.execSQL("CREATE INDEX index_holidays_endDate ON holidays(endDate)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE class_sessions ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE class_sessions ADD COLUMN reminderOffsetMinutes INTEGER"
            )
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = OFF")

            db.execSQL(
                """
                UPDATE tasks
                SET categoryId = (
                    SELECT MIN(duplicate.id)
                    FROM categories duplicate
                    JOIN categories original
                      ON lower(trim(duplicate.name)) = lower(trim(original.name))
                    WHERE original.id = tasks.categoryId
                )
                WHERE categoryId IS NOT NULL
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE class_sessions
                SET subjectId = (
                    SELECT MIN(duplicate.id)
                    FROM subjects duplicate
                    JOIN subjects original
                      ON lower(trim(duplicate.name)) = lower(trim(original.name))
                    WHERE original.id = class_sessions.subjectId
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE tasks
                SET subjectId = (
                    SELECT MIN(duplicate.id)
                    FROM subjects duplicate
                    JOIN subjects original
                      ON lower(trim(duplicate.name)) = lower(trim(original.name))
                    WHERE original.id = tasks.subjectId
                )
                WHERE subjectId IS NOT NULL
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE exams
                SET subjectId = (
                    SELECT MIN(duplicate.id)
                    FROM subjects duplicate
                    JOIN subjects original
                      ON lower(trim(duplicate.name)) = lower(trim(original.name))
                    WHERE original.id = exams.subjectId
                )
                WHERE subjectId IS NOT NULL
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE subjects_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL COLLATE NOCASE,
                    code TEXT,
                    color TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO subjects_new (id, name, code, color)
                SELECT id, trim(name), code, color
                FROM subjects
                WHERE id IN (
                    SELECT MIN(id)
                    FROM subjects
                    GROUP BY lower(trim(name))
                )
                """.trimIndent()
            )
            db.execSQL("DROP TABLE subjects")
            db.execSQL("ALTER TABLE subjects_new RENAME TO subjects")
            db.execSQL("CREATE UNIQUE INDEX index_subjects_name ON subjects(name)")

            db.execSQL(
                """
                CREATE TABLE categories_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL COLLATE NOCASE,
                    color TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO categories_new (id, name, color)
                SELECT id, trim(name), color
                FROM categories
                WHERE id IN (
                    SELECT MIN(id)
                    FROM categories
                    GROUP BY lower(trim(name))
                )
                """.trimIndent()
            )
            db.execSQL("DROP TABLE categories")
            db.execSQL("ALTER TABLE categories_new RENAME TO categories")
            db.execSQL("CREATE UNIQUE INDEX index_categories_name ON categories(name)")

            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN assignmentType INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER")
            db.execSQL("UPDATE tasks SET status = 4 WHERE done = 1")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exams ADD COLUMN endTime TEXT")
        }
    }
}
