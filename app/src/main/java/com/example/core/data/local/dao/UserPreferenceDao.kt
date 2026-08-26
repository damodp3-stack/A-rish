package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.local.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for User Preferences.
 */
@Dao
interface UserPreferenceDao {

    @Query("SELECT * FROM user_preferences WHERE id = :id")
    suspend fun getPreferenceById(id: String): UserPreferenceEntity?

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId ORDER BY domain ASC, preference_key ASC")
    fun getAllPreferences(userId: String): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId ORDER BY domain ASC, preference_key ASC")
    suspend fun getAllPreferencesList(userId: String): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId AND domain = :domain")
    suspend fun getPreferencesByDomain(userId: String, domain: String): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId AND domain = :domain AND preference_key = :key LIMIT 1")
    suspend fun getPreference(userId: String, domain: String, key: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: UserPreferenceEntity)

    @Update
    suspend fun updatePreference(preference: UserPreferenceEntity): Int

    @Query("DELETE FROM user_preferences WHERE id = :id")
    suspend fun deletePreference(id: String): Int

    @Query("DELETE FROM user_preferences WHERE user_id = :userId AND domain = :domain AND preference_key = :key")
    suspend fun deletePreferenceByKey(userId: String, domain: String, key: String): Int
}
