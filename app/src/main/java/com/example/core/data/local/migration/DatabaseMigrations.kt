package com.example.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Deterministic SQLite Database Migrations for A-RISH OS.
 */
object DatabaseMigrations {

    /**
     * Migration from Version 1 to Version 2:
     * Adds World Model tables (goals, projects, goal_project_links, commitments,
     * user_preferences, world_entities, entity_aliases) with full relational integrity.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. goals table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `goals` (
                    `id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `priority` TEXT NOT NULL,
                    `parent_goal_id` TEXT,
                    `target_deadline` INTEGER,
                    `progress_type` TEXT NOT NULL,
                    `progress_milestones_total` INTEGER NOT NULL,
                    `progress_milestones_completed` INTEGER NOT NULL,
                    `progress_manual_percentage` INTEGER NOT NULL,
                    `progress_manual_reasoning` TEXT,
                    `constraints_json` TEXT NOT NULL,
                    `provenance_source` TEXT NOT NULL,
                    `confidence_score` REAL NOT NULL,
                    `valid_from` INTEGER NOT NULL,
                    `valid_until` INTEGER,
                    `version` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `completed_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_user_id` ON `goals` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_status` ON `goals` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_parent_goal_id` ON `goals` (`parent_goal_id`)")

            // 2. projects table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `projects` (
                    `id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `primary_goal_id` TEXT,
                    `tags_json` TEXT NOT NULL,
                    `version` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `completed_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_user_id` ON `projects` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_status` ON `projects` (`status`)")

            // 3. goal_project_links junction table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `goal_project_links` (
                    `goal_id` TEXT NOT NULL,
                    `project_id` TEXT NOT NULL,
                    `linked_at` INTEGER NOT NULL,
                    PRIMARY KEY(`goal_id`, `project_id`),
                    FOREIGN KEY(`goal_id`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_project_links_goal_id` ON `goal_project_links` (`goal_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_project_links_project_id` ON `goal_project_links` (`project_id`)")

            // 4. commitments table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `commitments` (
                    `id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `due_timestamp` INTEGER NOT NULL,
                    `associated_project_id` TEXT,
                    `associated_goal_id` TEXT,
                    `is_completed` INTEGER NOT NULL,
                    `provenance_source` TEXT NOT NULL,
                    `confidence_score` REAL NOT NULL,
                    `valid_from` INTEGER NOT NULL,
                    `valid_until` INTEGER,
                    `version` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `completed_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitments_user_id` ON `commitments` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitments_due_timestamp` ON `commitments` (`due_timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitments_associated_project_id` ON `commitments` (`associated_project_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitments_associated_goal_id` ON `commitments` (`associated_goal_id`)")

            // 5. user_preferences table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `user_preferences` (
                    `id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `domain` TEXT NOT NULL,
                    `preference_key` TEXT NOT NULL,
                    `preference_value` TEXT NOT NULL,
                    `provenance_source` TEXT NOT NULL,
                    `confidence_score` REAL NOT NULL,
                    `valid_from` INTEGER NOT NULL,
                    `valid_until` INTEGER,
                    `version` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_preferences_user_id_domain_preference_key` ON `user_preferences` (`user_id`, `domain`, `preference_key`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_preferences_domain` ON `user_preferences` (`domain`)")

            // 6. world_entities table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `world_entities` (
                    `canonical_id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `primary_display_name` TEXT NOT NULL,
                    `external_identifiers_json` TEXT NOT NULL,
                    `metadata_json` TEXT NOT NULL,
                    `provenance_source` TEXT NOT NULL,
                    `confidence_score` REAL NOT NULL,
                    `valid_from` INTEGER NOT NULL,
                    `valid_until` INTEGER,
                    `version` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`canonical_id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_entities_user_id` ON `world_entities` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_entities_type` ON `world_entities` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_entities_primary_display_name` ON `world_entities` (`primary_display_name`)")

            // 7. entity_aliases table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `entity_aliases` (
                    `alias` TEXT NOT NULL,
                    `canonical_id` TEXT NOT NULL,
                    PRIMARY KEY(`alias`, `canonical_id`),
                    FOREIGN KEY(`canonical_id`) REFERENCES `world_entities`(`canonical_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_aliases_alias` ON `entity_aliases` (`alias`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_aliases_canonical_id` ON `entity_aliases` (`canonical_id`)")
        }
    }
}
