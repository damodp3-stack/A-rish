package com.example.core.data.local.mapper

import com.example.core.data.local.entity.CommitmentEntity
import com.example.core.data.local.entity.GoalEntity
import com.example.core.data.local.entity.ProjectEntity
import com.example.core.data.local.entity.UserPreferenceEntity
import com.example.core.data.local.entity.WorldEntityEntity
import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.Commitment
import com.example.core.domain.world.model.EpistemicProvenance
import com.example.core.domain.world.model.EpistemicSource
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
 * Deterministic, version-safe mappers for Phase 2A World Model models.
 */
object WorldModelMappers {

    // --- Goal Mappings ---

    fun GoalEntity.toDomain(): Goal {
        val st = runCatching { GoalStatus.valueOf(status) }.getOrDefault(GoalStatus.ACTIVE)
        val pr = runCatching { GoalPriority.valueOf(priority) }.getOrDefault(GoalPriority.NORMAL)
        val src = runCatching { EpistemicSource.valueOf(provenanceSource) }.getOrDefault(EpistemicSource.USER_EXPLICIT)
        val prov = EpistemicProvenance(
            source = src,
            confidenceScore = confidenceScore,
            validFrom = validFrom,
            validUntil = validUntil
        )

        val prog = when (progressType) {
            "NOT_STARTED" -> GoalProgress.NotStarted
            "MILESTONES" -> GoalProgress.DiscreteMilestones(
                totalMilestones = progressMilestonesTotal,
                completedMilestones = progressMilestonesCompleted
            )
            "MANUAL" -> GoalProgress.ManualAssessment(
                percentage = progressManualPercentage,
                reasoning = progressManualReasoning ?: "Manual assessment",
                assessedAt = updatedAt,
                assessorProvenance = prov
            )
            else -> GoalProgress.NotStarted
        }

        val parsedConstraints = deserializeConstraints(constraintsJson)

        return Goal(
            id = id,
            userId = UserId(userId),
            title = title,
            description = description,
            status = st,
            priority = pr,
            parentGoalId = parentGoalId,
            targetDeadline = targetDeadline,
            progress = prog,
            constraints = parsedConstraints,
            provenance = prov,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt
        )
    }

    fun Goal.toEntity(): GoalEntity {
        val (pType, totalM, compM, manPct, manReason) = when (val p = progress) {
            is GoalProgress.NotStarted -> Tuple5("NOT_STARTED", 0, 0, 0, null)
            is GoalProgress.DiscreteMilestones -> Tuple5("MILESTONES", p.totalMilestones, p.completedMilestones, 0, null)
            is GoalProgress.TaskDerived -> Tuple5("TASK_DERIVED", p.totalLinkedTasks, p.completedTasks, 0, null)
            is GoalProgress.ManualAssessment -> Tuple5("MANUAL", 0, 0, p.percentage, p.reasoning)
        }

        return GoalEntity(
            id = id,
            userId = userId.value,
            title = title,
            description = description,
            status = status.name,
            priority = priority.name,
            parentGoalId = parentGoalId,
            targetDeadline = targetDeadline,
            progressType = pType,
            progressMilestonesTotal = totalM,
            progressMilestonesCompleted = compM,
            progressManualPercentage = manPct,
            progressManualReasoning = manReason,
            constraintsJson = serializeConstraints(constraints),
            provenanceSource = provenance.source.name,
            confidenceScore = provenance.confidenceScore,
            validFrom = provenance.validFrom,
            validUntil = provenance.validUntil,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt
        )
    }

    // --- Project Mappings ---

    fun ProjectEntity.toDomain(): Project {
        val st = runCatching { ProjectStatus.valueOf(status) }.getOrDefault(ProjectStatus.ACTIVE)
        val parsedTags = parseJsonToStringList(tagsJson).toSet()

        return Project(
            id = id,
            userId = UserId(userId),
            name = name,
            description = description,
            status = st,
            primaryGoalId = primaryGoalId,
            tags = parsedTags,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt
        )
    }

    fun Project.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        userId = userId.value,
        name = name,
        description = description,
        status = status.name,
        primaryGoalId = primaryGoalId,
        tagsJson = serializeStringList(tags.toList()),
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt
    )

    // --- Commitment Mappings ---

    fun CommitmentEntity.toDomain(): Commitment {
        val src = runCatching { EpistemicSource.valueOf(provenanceSource) }.getOrDefault(EpistemicSource.USER_EXPLICIT)
        val prov = EpistemicProvenance(
            source = src,
            confidenceScore = confidenceScore,
            validFrom = validFrom,
            validUntil = validUntil
        )

        return Commitment(
            id = id,
            userId = UserId(userId),
            title = title,
            description = description,
            dueTimestamp = dueTimestamp,
            associatedProjectId = associatedProjectId,
            associatedGoalId = associatedGoalId,
            isCompleted = isCompleted,
            provenance = prov,
            version = version,
            createdAt = createdAt,
            completedAt = completedAt
        )
    }

    fun Commitment.toEntity(): CommitmentEntity = CommitmentEntity(
        id = id,
        userId = userId.value,
        title = title,
        description = description,
        dueTimestamp = dueTimestamp,
        associatedProjectId = associatedProjectId,
        associatedGoalId = associatedGoalId,
        isCompleted = isCompleted,
        provenanceSource = provenance.source.name,
        confidenceScore = provenance.confidenceScore,
        validFrom = provenance.validFrom,
        validUntil = provenance.validUntil,
        version = version,
        createdAt = createdAt,
        completedAt = completedAt
    )

    // --- User Preference Mappings ---

    fun UserPreferenceEntity.toDomain(): UserPreference {
        val dom = runCatching { PreferenceDomain.valueOf(domain) }.getOrDefault(PreferenceDomain.CUSTOM)
        val src = runCatching { EpistemicSource.valueOf(provenanceSource) }.getOrDefault(EpistemicSource.USER_EXPLICIT)
        val prov = EpistemicProvenance(
            source = src,
            confidenceScore = confidenceScore,
            validFrom = validFrom,
            validUntil = validUntil
        )

        return UserPreference(
            id = id,
            userId = UserId(userId),
            domain = dom,
            preferenceKey = preferenceKey,
            preferenceValue = preferenceValue,
            provenance = prov,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun UserPreference.toEntity(): UserPreferenceEntity = UserPreferenceEntity(
        id = id,
        userId = userId.value,
        domain = domain.name,
        preferenceKey = preferenceKey,
        preferenceValue = preferenceValue,
        provenanceSource = provenance.source.name,
        confidenceScore = provenance.confidenceScore,
        validFrom = provenance.validFrom,
        validUntil = provenance.validUntil,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // --- World Entity Mappings ---

    fun WorldEntityEntity.toDomain(aliases: Set<String> = emptySet()): WorldEntity {
        val et = runCatching { WorldEntityType.valueOf(type) }.getOrDefault(WorldEntityType.CUSTOM)
        val src = runCatching { EpistemicSource.valueOf(provenanceSource) }.getOrDefault(EpistemicSource.USER_EXPLICIT)
        val prov = EpistemicProvenance(
            source = src,
            confidenceScore = confidenceScore,
            validFrom = validFrom,
            validUntil = validUntil
        )

        val extIds = parseJsonToStringMap(externalIdentifiersJson)
        val meta = parseJsonToStringMap(metadataJson)

        return WorldEntity(
            canonicalId = canonicalId,
            userId = UserId(userId),
            type = et,
            primaryDisplayName = primaryDisplayName,
            aliases = aliases,
            externalIdentifiers = extIds,
            metadata = meta,
            provenance = prov,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun WorldEntity.toEntity(): WorldEntityEntity = WorldEntityEntity(
        canonicalId = canonicalId,
        userId = userId.value,
        type = type.name,
        primaryDisplayName = primaryDisplayName,
        externalIdentifiersJson = serializeStringMap(externalIdentifiers),
        metadataJson = serializeStringMap(metadata),
        provenanceSource = provenance.source.name,
        confidenceScore = provenance.confidenceScore,
        validFrom = provenance.validFrom,
        validUntil = provenance.validUntil,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // --- Versioned Constraint Serialization ---

    fun serializeConstraints(constraints: List<GoalConstraint>): String {
        val items = constraints.map { c ->
            when (c) {
                is GoalConstraint.AbsoluteDeadline -> "{\"type\":\"DEADLINE\",\"epoch\":${c.epochMillis}}"
                is GoalConstraint.RequiredDeviceCapability -> "{\"type\":\"CAPABILITY\",\"cap\":\"${escapeJson(c.capabilityId)}\"}"
                is GoalConstraint.TimeWindow -> "{\"type\":\"TIME_WINDOW\",\"start\":${c.startHourUtc},\"end\":${c.endHourUtc}}"
                is GoalConstraint.EntityDependency -> "{\"type\":\"ENTITY_DEPENDENCY\",\"entityId\":\"${escapeJson(c.canonicalEntityId)}\"}"
                is GoalConstraint.InformationalContext -> "{\"type\":\"INFORMATIONAL\",\"key\":\"${escapeJson(c.key)}\",\"desc\":\"${escapeJson(c.description)}\"}"
                is GoalConstraint.UnknownConstraint -> "{\"type\":\"UNKNOWN\",\"origType\":\"${escapeJson(c.typeName)}\",\"raw\":\"${escapeJson(c.rawData)}\"}"
            }
        }
        return "[${items.joinToString(",")}]"
    }

    fun deserializeConstraints(json: String): List<GoalConstraint> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val tokens = splitJsonTokens(inner)
        val list = mutableListOf<GoalConstraint>()
        for (token in tokens) {
            val map = parseJsonToSimpleMap(token)
            val type = map["type"] ?: "UNKNOWN"
            val constraint = when (type) {
                "DEADLINE" -> map["epoch"]?.toLongOrNull()?.let { GoalConstraint.AbsoluteDeadline(it) }
                "CAPABILITY" -> map["cap"]?.let { GoalConstraint.RequiredDeviceCapability(it) }
                "TIME_WINDOW" -> {
                    val s = map["start"]?.toIntOrNull() ?: 0
                    val e = map["end"]?.toIntOrNull() ?: 23
                    GoalConstraint.TimeWindow(s, e)
                }
                "ENTITY_DEPENDENCY" -> map["entityId"]?.let { GoalConstraint.EntityDependency(it) }
                "INFORMATIONAL" -> {
                    val k = map["key"] ?: "info"
                    val d = map["desc"] ?: ""
                    GoalConstraint.InformationalContext(k, d)
                }
                else -> GoalConstraint.UnknownConstraint(typeName = type, rawData = token)
            }
            if (constraint != null) {
                list.add(constraint)
            }
        }
        return list
    }

    // --- JSON Helpers ---

    private fun serializeStringList(list: List<String>): String =
        "[${list.joinToString(",") { "\"${escapeJson(it)}\"" }}]"

    private fun parseJsonToStringList(json: String): List<String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        return splitJsonTokens(inner).map { unescapeJson(it.trim().removeSurrounding("\"")) }
    }

    private fun serializeStringMap(map: Map<String, String>): String {
        val pairs = map.entries.map { "\"${escapeJson(it.key)}\":\"${escapeJson(it.value)}\"" }
        return "{${pairs.joinToString(",")}}"
    }

    private fun parseJsonToStringMap(json: String): Map<String, String> {
        val map = parseJsonToSimpleMap(json)
        return map
    }

    private fun parseJsonToSimpleMap(jsonStr: String): Map<String, String> {
        val trimmed = jsonStr.trim()
        val result = mutableMapOf<String, String>()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return result
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return result

        val tokens = splitJsonTokens(inner)
        for (token in tokens) {
            val colonIdx = token.indexOf(':')
            if (colonIdx != -1) {
                val key = unescapeJson(token.substring(0, colonIdx).trim().removeSurrounding("\""))
                val rawVal = token.substring(colonIdx + 1).trim().removeSurrounding("\"")
                result[key] = unescapeJson(rawVal)
            }
        }
        return result
    }

    private fun splitJsonTokens(input: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        var depth = 0
        var start = 0
        for (i in input.indices) {
            val c = input[i]
            if (c == '\"' && (i == 0 || input[i - 1] != '\\')) {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == '{' || c == '[') depth++
                else if (c == '}' || c == ']') depth--
                else if (c == ',' && depth == 0) {
                    tokens.add(input.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        if (start < input.length) {
            val last = input.substring(start).trim()
            if (last.isNotEmpty()) tokens.add(last)
        }
        return tokens
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun unescapeJson(s: String): String =
        s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
