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
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE class_sessions ADD COLUMN weekNumber INTEGER NOT NULL DEFAULT 0")
        }
    }
}
