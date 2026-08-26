package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * SQLite Entity storing aliases mapping to canonical World Entities.
 */
@Entity(
    tableName = "entity_aliases",
    primaryKeys = ["alias", "canonical_id"],
    foreignKeys = [
        ForeignKey(
            entity = WorldEntityEntity::class,
            parentColumns = ["canonical_id"],
            childColumns = ["canonical_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["alias"]),
        Index(value = ["canonical_id"])
    ]
)
data class EntityAliasEntity(
    @ColumnInfo(name = "alias")
    val alias: String,

    @ColumnInfo(name = "canonical_id")
    val canonicalId: String
)
