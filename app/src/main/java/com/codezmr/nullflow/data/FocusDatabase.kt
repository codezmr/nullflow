package com.codezmr.nullflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FocusProfile::class, BlockedApp::class, FocusSession::class,
        InterceptLog::class, FocusSchedule::class
    ],
    version = 6,
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
         * concept existed). Non-destructive - user history is preserved.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN endReason TEXT")
            }
        }

        /**
         * v5 → v6: add the focus_schedules table (recurring focus windows).
         * Purely additive - no existing table is touched, so no user data is
         * at risk. Times are minutes-from-midnight, days are a 7-bit mask.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_schedules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`startMinute` INTEGER NOT NULL, " +
                        "`endMinute` INTEGER NOT NULL, " +
                        "`daysBitmask` INTEGER NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_schedules_profileId` " +
                        "ON `focus_schedules` (`profileId`)"
                )
            }
        }

        fun get(context: Context): FocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    "nullflow.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
