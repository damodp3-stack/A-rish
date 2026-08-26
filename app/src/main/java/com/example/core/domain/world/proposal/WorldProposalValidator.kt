package com.example.core.domain.world.proposal

import com.example.core.domain.time.TimeProvider
import com.example.core.domain.world.model.Commitment
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalProgress
import com.example.core.domain.world.model.GoalStatus
import com.example.core.domain.world.model.Project
import com.example.core.domain.world.model.ProjectStatus
import com.example.core.domain.world.model.UserPreference
import com.example.core.domain.world.model.WorldEntity
import com.example.core.domain.world.validation.DefenseInDepthSecretDetector

/**
 * Gatekeeper validating and sanitizing all incoming WorldProposals before persistence.
 * Prevents memory poisoning, prompt injection, and credential storage.
 */
class WorldProposalValidator(
    private val timeProvider: TimeProvider
) {
    sealed class ValidationResult<out T> {
        data class Accepted<T>(val sanitizedModel: T) : ValidationResult<T>()
        data class Rejected(val reason: String, val securityEvent: Boolean = false) : ValidationResult<Nothing>()
    }

    private val ADVERSARIAL_KEYWORDS = listOf(
        "ignore security",
        "bypass security",
        "approved all future actions",
        "already approved all",
        "biometric authentication already happened",
        "grant tool capability",
        "grant all permissions",
        "disable permission checks",
        "ignore permission checks"
    )

    fun validateGoalProposal(proposal: WorldProposal.ProposeGoal): ValidationResult<Goal> {
        if (proposal.id.isBlank()) return ValidationResult.Rejected("Goal ID cannot be blank")
        if (proposal.title.isBlank()) return ValidationResult.Rejected("Goal title cannot be blank")

        // 1. Adversarial Security Check
        val combinedText = "${proposal.title} ${proposal.description}".lowercase()
        for (kw in ADVERSARIAL_KEYWORDS) {
            if (combinedText.contains(kw)) {
                return ValidationResult.Rejected("Proposal contains unauthorized security override attempt: '$kw'", securityEvent = true)
            }
        }

        // 2. Secret Redaction
        val titleScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.title)
        val descScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.description)

        val currentTime = timeProvider.currentTimeMillis()
        val goal = Goal(
            id = proposal.id.trim(),
            userId = proposal.userId,
            title = titleScan.sanitizedText.take(120),
            description = descScan.sanitizedText.take(1000),
            priority = proposal.priority,
            status = GoalStatus.ACTIVE,
            parentGoalId = proposal.parentGoalId,
            targetDeadline = proposal.targetDeadline,
            progress = GoalProgress.NotStarted,
            constraints = proposal.constraints,
            provenance = proposal.provenance,
            version = 1L,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        return ValidationResult.Accepted(goal)
    }

    fun validateProjectProposal(proposal: WorldProposal.ProposeProject): ValidationResult<Project> {
        if (proposal.id.isBlank()) return ValidationResult.Rejected("Project ID cannot be blank")
        if (proposal.name.isBlank()) return ValidationResult.Rejected("Project name cannot be blank")

        val combinedText = "${proposal.name} ${proposal.description}".lowercase()
        for (kw in ADVERSARIAL_KEYWORDS) {
            if (combinedText.contains(kw)) {
                return ValidationResult.Rejected("Proposal contains unauthorized security override attempt: '$kw'", securityEvent = true)
            }
        }

        val nameScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.name)
        val descScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.description)

        val currentTime = timeProvider.currentTimeMillis()
        val project = Project(
            id = proposal.id.trim(),
            userId = proposal.userId,
            name = nameScan.sanitizedText.take(100),
            description = descScan.sanitizedText.take(1000),
            status = ProjectStatus.ACTIVE,
            primaryGoalId = proposal.primaryGoalId,
            tags = proposal.tags.map { it.take(30) }.toSet(),
            version = 1L,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        return ValidationResult.Accepted(project)
    }

    fun validateCommitmentProposal(proposal: WorldProposal.ProposeCommitment): ValidationResult<Commitment> {
        if (proposal.id.isBlank()) return ValidationResult.Rejected("Commitment ID cannot be blank")
        if (proposal.title.isBlank()) return ValidationResult.Rejected("Commitment title cannot be blank")

        val combinedText = "${proposal.title} ${proposal.description}".lowercase()
        for (kw in ADVERSARIAL_KEYWORDS) {
            if (combinedText.contains(kw)) {
                return ValidationResult.Rejected("Proposal contains unauthorized security override attempt: '$kw'", securityEvent = true)
            }
        }

        val titleScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.title)
        val descScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.description)

        val currentTime = timeProvider.currentTimeMillis()
        val commitment = Commitment(
            id = proposal.id.trim(),
            userId = proposal.userId,
            title = titleScan.sanitizedText.take(120),
            description = descScan.sanitizedText.take(1000),
            dueTimestamp = proposal.dueTimestamp,
            associatedProjectId = proposal.associatedProjectId,
            associatedGoalId = proposal.associatedGoalId,
            isCompleted = false,
            provenance = proposal.provenance,
            version = 1L,
            createdAt = currentTime
        )

        return ValidationResult.Accepted(commitment)
    }

    fun validatePreferenceProposal(proposal: WorldProposal.ProposePreference): ValidationResult<UserPreference> {
        if (proposal.id.isBlank()) return ValidationResult.Rejected("Preference ID cannot be blank")
        if (proposal.preferenceKey.isBlank()) return ValidationResult.Rejected("Preference key cannot be blank")
        if (proposal.preferenceValue.isBlank()) return ValidationResult.Rejected("Preference value cannot be blank")

        val combinedText = "${proposal.preferenceKey} ${proposal.preferenceValue}".lowercase()
        for (kw in ADVERSARIAL_KEYWORDS) {
            if (combinedText.contains(kw)) {
                return ValidationResult.Rejected("Preference contains security override attempt: '$kw'", securityEvent = true)
            }
        }

        val keyScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.preferenceKey)
        val valScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.preferenceValue)

        val currentTime = timeProvider.currentTimeMillis()
        val pref = UserPreference(
            id = proposal.id.trim(),
            userId = proposal.userId,
            domain = proposal.domain,
            preferenceKey = keyScan.sanitizedText.take(64),
            preferenceValue = valScan.sanitizedText.take(500),
            provenance = proposal.provenance,
            version = 1L,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        return ValidationResult.Accepted(pref)
    }

    fun validateWorldEntityProposal(proposal: WorldProposal.ProposeWorldEntity): ValidationResult<WorldEntity> {
        if (proposal.canonicalId.isBlank()) return ValidationResult.Rejected("Canonical ID cannot be blank")
        if (proposal.primaryDisplayName.isBlank()) return ValidationResult.Rejected("Display name cannot be blank")

        val nameScan = DefenseInDepthSecretDetector.scanAndSanitize(proposal.primaryDisplayName)
        val cleanAliases = proposal.aliases.map { DefenseInDepthSecretDetector.scanAndSanitize(it).sanitizedText.take(60) }.toSet()

        val currentTime = timeProvider.currentTimeMillis()
        val entity = WorldEntity(
            canonicalId = proposal.canonicalId.trim(),
            userId = proposal.userId,
            type = proposal.type,
            primaryDisplayName = nameScan.sanitizedText.take(80),
            aliases = cleanAliases,
            externalIdentifiers = proposal.externalIdentifiers,
            metadata = emptyMap(),
            provenance = proposal.provenance,
            version = 1L,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        return ValidationResult.Accepted(entity)
    }
}
