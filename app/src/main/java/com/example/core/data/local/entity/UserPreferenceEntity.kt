package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting User Preferences in A-RISH World Model.
 */
@Entity(
    tableName = "user_preferences",
    indices = [
        Index(value = ["user_id", "domain", "preference_key"], unique = true),
        Index(value = ["domain"])
    ]
)
data class UserPreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "preference_key")
    val preferenceKey: String,

    @ColumnInfo(name = "preference_value")
    val preferenceValue: String,

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
