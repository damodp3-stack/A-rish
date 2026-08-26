package com.example.core.domain.world.proposal

import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.Commitment
import com.example.core.domain.world.model.EpistemicProvenance
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalConstraint
import com.example.core.domain.world.model.GoalPriority
import com.example.core.domain.world.model.GoalProgress
import com.example.core.domain.world.model.GoalStatus
import com.example.core.domain.world.model.PreferenceDomain
import com.example.core.domain.world.model.Project
import com.example.core.domain.world.model.ProjectStatus
import com.example.core.domain.world.model.UserPreference
import com.example.core.domain.world.model.WorldEntity
import com.example.core.domain.world.model.WorldEntityType

/**
 * Sealed hierarchy of unvalidated World Model modification proposals.
 * Represents proposals from AI inference or external observation before persistence validation.
 */
sealed class WorldProposal {

    data class ProposeGoal(
        val id: String,
        val userId: UserId,
        val title: String,
        val description: String,
        val priority: GoalPriority = GoalPriority.NORMAL,
        val parentGoalId: String? = null,
        val targetDeadline: Long? = null,
        val constraints: List<GoalConstraint> = emptyList(),
        val provenance: EpistemicProvenance
    ) : WorldProposal()

    data class ProposeProject(
        val id: String,
        val userId: UserId,
        val name: String,
        val description: String,
        val primaryGoalId: String? = null,
        val tags: Set<String> = emptySet()
    ) : WorldProposal()

    data class ProposeCommitment(
        val id: String,
        val userId: UserId,
        val title: String,
        val description: String,
        val dueTimestamp: Long,
        val associatedProjectId: String? = null,
        val associatedGoalId: String? = null,
        val provenance: EpistemicProvenance
    ) : WorldProposal()

    data class ProposePreference(
        val id: String,
        val userId: UserId,
        val domain: PreferenceDomain,
        val preferenceKey: String,
        val preferenceValue: String,
        val provenance: EpistemicProvenance
    ) : WorldProposal()

    data class ProposeWorldEntity(
        val canonicalId: String,
        val userId: UserId,
        val type: WorldEntityType,
        val primaryDisplayName: String,
        val aliases: Set<String> = emptySet(),
        val externalIdentifiers: Map<String, String> = emptyMap(),
        val provenance: EpistemicProvenance
    ) : WorldProposal()
}
