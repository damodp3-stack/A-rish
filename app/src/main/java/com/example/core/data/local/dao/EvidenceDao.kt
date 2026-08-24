package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.EvidenceEntity

@Dao
interface EvidenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidenceList(evidenceList: List<EvidenceEntity>)

    @Query("SELECT * FROM verification_evidence WHERE step_id = :stepId ORDER BY captured_at ASC")
    suspend fun getEvidenceForStep(stepId: String): List<EvidenceEntity>

    @Query("SELECT * FROM verification_evidence WHERE evidence_id = :evidenceId")
    suspend fun getEvidenceById(evidenceId: String): EvidenceEntity?
}
