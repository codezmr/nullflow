package com.codezmr.nullflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FocusProfile::class, BlockedApp::class, FocusSession::class, InterceptLog::class],
    version = 5,
    exportSchema = false
)
abstract class FocusDatabase : RoomDatabase() {

    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile
        private var INSTANCE: FocusDatabase? = null

        /**
         * v4 → v5: add the endReason column to focus_sessions (Strict Mode
         * session accounting). Existing rows get NULL (they ended before the
         * concept existed). Non-destructive — user history is preserved.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN endReason TEXT")
            }
        }

        fun get(context: Context): FocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    "nullflow.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
