package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.local.dao.AgentEventDao
import com.example.core.data.local.dao.ApprovalDao
import com.example.core.data.local.dao.CommitmentDao
import com.example.core.data.local.dao.EvidenceDao
import com.example.core.data.local.dao.GoalDao
import com.example.core.data.local.dao.GoalProjectLinkDao
import com.example.core.data.local.dao.IdempotencyDao
import com.example.core.data.local.dao.MemoryDao
import com.example.core.data.local.dao.ProjectDao
import com.example.core.data.local.dao.StepDao
import com.example.core.data.local.dao.TaskDao
import com.example.core.data.local.dao.UserPreferenceDao
import com.example.core.data.local.dao.WorldEntityDao
import com.example.core.data.local.entity.AgentEventEntity
import com.example.core.data.local.entity.ApprovalEntity
import com.example.core.data.local.entity.CommitmentEntity
import com.example.core.data.local.entity.EntityAliasEntity
import com.example.core.data.local.entity.EvidenceEntity
import com.example.core.data.local.entity.GoalEntity
import com.example.core.data.local.entity.GoalProjectLinkEntity
import com.example.core.data.local.entity.IdempotencyEntity
import com.example.core.data.local.entity.MemoryEntity
import com.example.core.data.local.entity.ProjectEntity
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import com.example.core.data.local.entity.UserPreferenceEntity
import com.example.core.data.local.entity.WorldEntityEntity
import com.example.core.data.local.fts.MemoryFtsEntity
import com.example.core.data.local.migration.DatabaseMigrations

/**
 * A-RISH Master SQLite Database with full FTS lexical indexing, foreign-key safety,
 * atomic step checkpointing, durable idempotency constraints, versioned migrations,
 * and authoritative World Model + Goal Intelligence storage (Phase 2A).
 *
 * Production Invariants:
 * 1. exportSchema = true (Room tracks schema definition snapshots in the repository)
 * 2. NO fallbackToDestructiveMigration() in production builder.
 * 3. Atomic append-only audit event logging for all approval state transitions.
 */
@Database(
    entities = [
        TaskEntity::class,
        StepEntity::class,
        ApprovalEntity::class,
        IdempotencyEntity::class,
        EvidenceEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        AgentEventEntity::class,
        // Phase 2A Entities
        GoalEntity::class,
        ProjectEntity::class,
        GoalProjectLinkEntity::class,
        CommitmentEntity::class,
        UserPreferenceEntity::class,
        WorldEntityEntity::class,
        EntityAliasEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class ArishDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun stepDao(): StepDao
    abstract fun approvalDao(): ApprovalDao
    abstract fun idempotencyDao(): IdempotencyDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentEventDao(): AgentEventDao

    // Phase 2A DAOs
    abstract fun goalDao(): GoalDao
    abstract fun projectDao(): ProjectDao
    abstract fun goalProjectLinkDao(): GoalProjectLinkDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun worldEntityDao(): WorldEntityDao

    companion object {
        const val DATABASE_NAME = "arish_os.db"

        @Volatile
        private var INSTANCE: ArishDatabase? = null

        fun getInstance(context: Context): ArishDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ArishDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(DatabaseMigrations.MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
