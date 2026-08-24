package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity recording evidence items supporting verified execution states.
 */
@Entity(
    tableName = "verification_evidence",
    indices = [
        Index(value = ["step_id"])
    ]
)
data class EvidenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "evidence_id")
    val evidenceId: String,

    @ColumnInfo(name = "step_id")
    val stepId: String,

    @ColumnInfo(name = "evidence_type")
    val evidenceType: String, // LOCAL_DATABASE_ROW, SYSTEM_INTENT_RESOLVED, HTTP_STATUS_200, etc.

    @ColumnInfo(name = "confidence")
    val confidence: String, // CERTAIN, PROBABLE, INDETERMINATE, UNVERIFIED

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "artifact_uri")
    val artifactUri: String? = null,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long
)
