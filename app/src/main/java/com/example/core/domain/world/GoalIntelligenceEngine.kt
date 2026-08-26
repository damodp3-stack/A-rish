package com.example.core.domain.world

import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.Commitment
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalProgress
import com.example.core.domain.world.model.Project
import com.example.core.domain.world.model.UserPreference
import com.example.core.domain.world.model.WorldEntity
import com.example.core.domain.world.proposal.WorldProposal
import com.example.core.domain.world.proposal.WorldProposalValidator
import com.example.core.domain.world.repository.GoalRepository
import com.example.core.domain.world.repository.ProjectRepository
import com.example.core.domain.world.repository.WorldModelRepository
import com.example.core.domain.world.repository.WorldPlanningContext

/**
 * Domain engine orchestrating Goal Intelligence & World Model state transitions.
 * Integrates validation, provenance verification, progress derivation, and planning snapshots.
 */
class GoalIntelligenceEngine(
    private val goalRepository: GoalRepository,
    private val projectRepository: ProjectRepository,
    private val worldModelRepository: WorldModelRepository,
    private val proposalValidator: WorldProposalValidator
) {

    /**
     * Processes and stores a proposed Goal with security & secret validation.
     */
    suspend fun proposeAndSaveGoal(proposal: WorldProposal.ProposeGoal): Result<Goal> = runCatching {
        val validation = proposalValidator.validateGoalProposal(proposal)
        when (validation) {
            is WorldProposalValidator.ValidationResult.Rejected -> {
                throw IllegalArgumentException("Goal proposal rejected: ${validation.reason}")
            }
            is WorldProposalValidator.ValidationResult.Accepted -> {
                val goal = validation.sanitizedModel
                goalRepository.createGoal(goal).getOrThrow()
                goal
            }
        }
    }

    /**
     * Processes and stores a proposed Project.
     */
    suspend fun proposeAndSaveProject(proposal: WorldProposal.ProposeProject): Result<Project> = runCatching {
        val validation = proposalValidator.validateProjectProposal(proposal)
        when (validation) {
            is WorldProposalValidator.ValidationResult.Rejected -> {
                throw IllegalArgumentException("Project proposal rejected: ${validation.reason}")
            }
            is WorldProposalValidator.ValidationResult.Accepted -> {
                val project = validation.sanitizedModel
                projectRepository.saveProject(project).getOrThrow()
                project
            }
        }
    }

    /**
     * Processes and stores a proposed Commitment.
     */
    suspend fun proposeAndSaveCommitment(proposal: WorldProposal.ProposeCommitment): Result<Commitment> = runCatching {
        val validation = proposalValidator.validateCommitmentProposal(proposal)
        when (validation) {
            is WorldProposalValidator.ValidationResult.Rejected -> {
                throw IllegalArgumentException("Commitment proposal rejected: ${validation.reason}")
            }
            is WorldProposalValidator.ValidationResult.Accepted -> {
                val commitment = validation.sanitizedModel
                worldModelRepository.saveCommitment(commitment).getOrThrow()
                commitment
            }
        }
    }

    /**
     * Processes and stores a proposed Preference.
     */
    suspend fun proposeAndSavePreference(proposal: WorldProposal.ProposePreference): Result<UserPreference> = runCatching {
        val validation = proposalValidator.validatePreferenceProposal(proposal)
        when (validation) {
            is WorldProposalValidator.ValidationResult.Rejected -> {
                throw IllegalArgumentException("Preference proposal rejected: ${validation.reason}")
            }
            is WorldProposalValidator.ValidationResult.Accepted -> {
                val pref = validation.sanitizedModel
                worldModelRepository.savePreference(pref).getOrThrow()
                pref
            }
        }
    }

    /**
     * Processes and stores a proposed World Entity with alias mapping.
     */
    suspend fun proposeAndSaveWorldEntity(proposal: WorldProposal.ProposeWorldEntity): Result<WorldEntity> = runCatching {
        val validation = proposalValidator.validateWorldEntityProposal(proposal)
        when (validation) {
            is WorldProposalValidator.ValidationResult.Rejected -> {
                throw IllegalArgumentException("Entity proposal rejected: ${validation.reason}")
            }
            is WorldProposalValidator.ValidationResult.Accepted -> {
                val entity = validation.sanitizedModel
                worldModelRepository.saveWorldEntity(entity).getOrThrow()
                entity
            }
        }
    }

    /**
     * Updates progress towards an existing goal with derived status calculation.
     */
    suspend fun updateGoalProgress(goalId: String, progress: GoalProgress): Result<Goal> {
        return goalRepository.updateGoalProgress(goalId, progress)
    }

    /**
     * Generates a safe, bounded snapshot of user goals, upcoming commitments, and preferences.
     */
    suspend fun getPlanningContext(userId: UserId): WorldPlanningContext {
        return worldModelRepository.getPlanningContext(userId)
    }
}
