package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.data.local.entity.EntityAliasEntity
import com.example.core.data.local.entity.WorldEntityEntity

/**
 * Room Data Access Object for World Entities and Alias mapping.
 */
@Dao
interface WorldEntityDao {

    @Query("SELECT * FROM world_entities WHERE canonical_id = :id")
    suspend fun getEntityById(id: String): WorldEntityEntity?

    @Query("SELECT * FROM world_entities WHERE user_id = :userId AND type = :type")
    suspend fun getEntitiesByType(userId: String, type: String): List<WorldEntityEntity>

    @Query("SELECT * FROM entity_aliases WHERE canonical_id = :canonicalId")
    suspend fun getAliasesForEntity(canonicalId: String): List<EntityAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntity(entity: WorldEntityEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAliases(aliases: List<EntityAliasEntity>)

    @Update
    suspend fun updateEntity(entity: WorldEntityEntity): Int

    @Query("DELETE FROM world_entities WHERE canonical_id = :id")
    suspend fun deleteEntity(id: String): Int

    @Query("DELETE FROM entity_aliases WHERE canonical_id = :canonicalId")
    suspend fun deleteAliasesForEntity(canonicalId: String): Int

    @Query("""
        SELECT DISTINCT e.* FROM world_entities e
        LEFT JOIN entity_aliases a ON e.canonical_id = a.canonical_id
        WHERE e.user_id = :userId AND (
            e.canonical_id = :query 
            OR LOWER(e.primary_display_name) = LOWER(:query) 
            OR LOWER(a.alias) = LOWER(:query)
            OR LOWER(e.primary_display_name) LIKE '%' || LOWER(:query) || '%'
        )
    """)
    suspend fun searchEntities(userId: String, query: String): List<WorldEntityEntity>

    @Transaction
    suspend fun saveEntityWithAliases(entity: WorldEntityEntity, aliases: List<String>) {
        insertEntity(entity)
        deleteAliasesForEntity(entity.canonicalId)
        if (aliases.isNotEmpty()) {
            val aliasEntities = aliases.map { EntityAliasEntity(alias = it, canonicalId = entity.canonicalId) }
            insertAliases(aliasEntities)
        }
    }
}
