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
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
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
}
