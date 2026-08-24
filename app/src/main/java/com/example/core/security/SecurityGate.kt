package com.example.core.security

import com.example.core.domain.error.ArishException
import com.example.core.domain.security.ApprovalRequest
import com.example.core.domain.security.ApprovalStatus
import com.example.core.domain.security.AuthenticationRequirement
import com.example.core.domain.security.PermissionBroker
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.PermissionStatus
import com.example.core.domain.security.RiskEvaluation
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.security.SecurityAuthenticator

/**
 * Result of comprehensive security policy evaluation before tool execution.
 */
sealed class SecurityGateDecision {
    data object Permitted : SecurityGateDecision()
    data class RequiresApproval(val approvalRequest: ApprovalRequest) : SecurityGateDecision()
    data class Blocked(val exception: ArishException) : SecurityGateDecision()
}

/**
 * Production security enforcement gate coordinating Risk, Approvals, Permissions, and Authentications.
 *
 * NON-NEGOTIABLE SECURITY INVARIANTS:
 * 1. Android OS permission and A-RISH user authorization are separate security layers.
 * 2. HIGH/CRITICAL risk actions MUST require explicit approval/authentication even if OS permission is already granted.
 * 3. CRITICAL risk operations require stronger authentication (BIOMETRIC or DEVICE_CREDENTIAL).
 * 4. Missing or denied OS permissions produce typed failures.
 * 5. Security failures must NEVER be converted into successful execution states.
 */
class SecurityGate(
    private val permissionBroker: PermissionBroker,
    private val authenticator: SecurityAuthenticator
) {

    suspend fun evaluateAndEnforce(
        taskId: String,
        stepId: String,
        toolId: String,
        capabilityId: String,
        riskEvaluation: RiskEvaluation,
        permissionRequirements: List<PermissionRequirement>,
        authenticationRequirement: AuthenticationRequirement,
        existingApproval: ApprovalRequest? = null
    ): SecurityGateDecision {

        // 1. Check OS Runtime Permissions first
        for (req in permissionRequirements) {
            val status = permissionBroker.checkPermission(req.permissionManifestKey)
            if (status != PermissionStatus.GRANTED) {
                val requestedStatus = permissionBroker.requestPermission(
                    req.permissionManifestKey,
                    req.rationaleUserText
                )
                if (requestedStatus != PermissionStatus.GRANTED) {
                    return SecurityGateDecision.Blocked(
                        ArishException.PermissionDeniedException(
                            req.permissionManifestKey,
                            "Required permission is not granted by user: ${req.rationaleUserText}"
                        )
                    )
                }
            }
        }

        // 2. Enforce Approval for HIGH and CRITICAL risk operations (even if OS permission is granted)
        if (riskEvaluation.level.requiresExplicitApproval) {
            val currentTime = System.currentTimeMillis()
            val hasValidApprovedApproval = existingApproval != null &&
                existingApproval.isValidForExecution(currentTime) &&
                (existingApproval.decision == null || existingApproval.decision.status == ApprovalStatus.APPROVED) &&
                existingApproval.taskId == taskId &&
                existingApproval.stepId == stepId &&
                existingApproval.toolId == toolId &&
                existingApproval.capabilityId == capabilityId &&
                existingApproval.riskEvaluation.level == riskEvaluation.level

            if (!hasValidApprovedApproval) {
                val approvalReq = if (existingApproval != null &&
                    existingApproval.isPendingValid(currentTime) &&
                    existingApproval.taskId == taskId &&
                    existingApproval.stepId == stepId &&
                    existingApproval.toolId == toolId &&
                    existingApproval.capabilityId == capabilityId
                ) {
                    existingApproval
                } else {
                    ApprovalRequest(
                        approvalId = "appr-$taskId-$stepId",
                        taskId = taskId,
                        stepId = stepId,
                        toolId = toolId,
                        capabilityId = capabilityId,
                        riskEvaluation = riskEvaluation,
                        actionSummary = riskEvaluation.explanationText,
                        previewPayload = emptyMap(),
                        createdAt = currentTime,
                        expiresAt = currentTime + 60_000L,
                        status = if (existingApproval?.isExpiredAt(currentTime) == true) ApprovalStatus.EXPIRED else ApprovalStatus.PENDING
                    )
                }
                return SecurityGateDecision.RequiresApproval(approvalReq)
            }
        }

        // 3. Enforce Authentication Requirement (e.g. USER_CONFIRMATION, BIOMETRIC)
        // INVARIANT: Authentication requirements must NEVER be downgraded.
        // CRITICAL:
        //   NONE -> BIOMETRIC
        //   USER_CONFIRMATION -> BIOMETRIC
        //   DEVICE_CREDENTIAL -> DEVICE_CREDENTIAL
        //   BIOMETRIC -> BIOMETRIC
        // HIGH:
        //   NONE -> USER_CONFIRMATION
        //   USER_CONFIRMATION / DEVICE_CREDENTIAL / BIOMETRIC -> allowed
        val effectiveAuthReq = when (riskEvaluation.level) {
            RiskLevel.CRITICAL -> when (authenticationRequirement) {
                AuthenticationRequirement.NONE, AuthenticationRequirement.USER_CONFIRMATION -> AuthenticationRequirement.BIOMETRIC
                AuthenticationRequirement.DEVICE_CREDENTIAL -> AuthenticationRequirement.DEVICE_CREDENTIAL
                AuthenticationRequirement.BIOMETRIC -> AuthenticationRequirement.BIOMETRIC
            }
            RiskLevel.HIGH -> when (authenticationRequirement) {
                AuthenticationRequirement.NONE -> AuthenticationRequirement.USER_CONFIRMATION
                AuthenticationRequirement.USER_CONFIRMATION -> AuthenticationRequirement.USER_CONFIRMATION
                AuthenticationRequirement.DEVICE_CREDENTIAL -> AuthenticationRequirement.DEVICE_CREDENTIAL
                AuthenticationRequirement.BIOMETRIC -> AuthenticationRequirement.BIOMETRIC
            }
            RiskLevel.MEDIUM, RiskLevel.LOW -> authenticationRequirement
        }

        if (effectiveAuthReq != AuthenticationRequirement.NONE) {
            val authResult = authenticator.authenticate(
                requirement = effectiveAuthReq,
                promptTitle = "Authorize $toolId Action",
                promptSubtitle = riskEvaluation.explanationText
            )
            if (!authResult.isSuccess) {
                return SecurityGateDecision.Blocked(
                    ArishException.AuthenticationRequiredException(
                        requirement = effectiveAuthReq.name,
                        message = "Security authentication failed or was cancelled by user"
                    )
                )
            }
        }

        return SecurityGateDecision.Permitted
    }
}
