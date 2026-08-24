package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.local.dao.AgentEventDao
import com.example.core.data.local.dao.ApprovalDao
import com.example.core.data.local.dao.EvidenceDao
import com.example.core.data.local.dao.IdempotencyDao
import com.example.core.data.local.dao.MemoryDao
import com.example.core.data.local.dao.StepDao
import com.example.core.data.local.dao.TaskDao
import com.example.core.data.local.entity.AgentEventEntity
import com.example.core.data.local.entity.ApprovalEntity
import com.example.core.data.local.entity.EvidenceEntity
import com.example.core.data.local.entity.IdempotencyEntity
import com.example.core.data.local.entity.MemoryEntity
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import com.example.core.data.local.fts.MemoryFtsEntity

/**
 * A-RISH Master SQLite Database with full FTS lexical indexing, foreign-key safety,
 * atomic step checkpointing, and durable idempotency constraints.
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
        AgentEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ArishDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun stepDao(): StepDao
    abstract fun approvalDao(): ApprovalDao
    abstract fun idempotencyDao(): IdempotencyDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentEventDao(): AgentEventDao

    companion object {
        @Volatile
        private var INSTANCE: ArishDatabase? = null

        fun getInstance(context: Context): ArishDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ArishDatabase::class.java,
                    "arish_os.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
