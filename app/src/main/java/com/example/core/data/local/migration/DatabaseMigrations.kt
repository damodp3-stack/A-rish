package com.example.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Deterministic SQLite Database Migrations for A-RISH OS.
 */
object DatabaseMigrations {

    /**
     * Migration from Version 1 to Version 2 (Adds session correlation indices to agent events).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_events_session` ON `agent_events` (`task_id`, `step_id`, `timestamp`)")
        }
    }
}
