package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting World Entities in A-RISH World Model.
 */
@Entity(
    tableName = "world_entities",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["type"]),
        Index(value = ["primary_display_name"])
    ]
)
data class WorldEntityEntity(
    @PrimaryKey
    @ColumnInfo(name = "canonical_id")
    val canonicalId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "primary_display_name")
    val primaryDisplayName: String,

    @ColumnInfo(name = "external_identifiers_json")
    val externalIdentifiersJson: String,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String,

    @ColumnInfo(name = "provenance_source")
    val provenanceSource: String,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "valid_from")
    val validFrom: Long,

    @ColumnInfo(name = "valid_until")
    val validUntil: Long?,

    @ColumnInfo(name = "version")
    val version: Long = 1L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
