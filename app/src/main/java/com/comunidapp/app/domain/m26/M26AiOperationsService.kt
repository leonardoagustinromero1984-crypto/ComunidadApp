package com.comunidapp.app.domain.m26

import com.comunidapp.app.data.model.M26AiJobStatus
import com.comunidapp.app.data.model.M26AiResultStatus
import com.comunidapp.app.data.model.M26ReviewDecision

object M26JobLifecycle {
    fun validateJobTransition(current: M26AiJobStatus, target: M26AiJobStatus): String? {
        if (current == target) return null
        if (current.isTerminal()) return "M26_JOB_TERMINAL"
        return when (current) {
            M26AiJobStatus.QUEUED -> if (target in setOf(M26AiJobStatus.RUNNING, M26AiJobStatus.CANCELLED, M26AiJobStatus.EXPIRED)) null else "M26_INVALID_JOB_TRANSITION"
            M26AiJobStatus.RUNNING -> if (target in setOf(M26AiJobStatus.COMPLETED, M26AiJobStatus.FAILED, M26AiJobStatus.CANCELLED)) null else "M26_INVALID_JOB_TRANSITION"
            else -> "M26_INVALID_JOB_TRANSITION"
        }
    }

    fun validateResultTransition(current: M26AiResultStatus, target: M26AiResultStatus): String? {
        if (current == target) return null
        if (current.isTerminal()) return "M26_RESULT_TERMINAL"
        return when (current) {
            M26AiResultStatus.DRAFT -> if (target in setOf(M26AiResultStatus.PENDING_REVIEW, M26AiResultStatus.ARCHIVED)) null else "M26_INVALID_RESULT_TRANSITION"
            M26AiResultStatus.PENDING_REVIEW -> if (target in setOf(M26AiResultStatus.APPROVED, M26AiResultStatus.REJECTED, M26AiResultStatus.ARCHIVED)) null else "M26_INVALID_RESULT_TRANSITION"
            M26AiResultStatus.APPROVED -> if (target == M26AiResultStatus.ARCHIVED) null else "M26_INVALID_RESULT_TRANSITION"
            M26AiResultStatus.REJECTED -> if (target == M26AiResultStatus.ARCHIVED) null else "M26_INVALID_RESULT_TRANSITION"
            else -> "M26_INVALID_RESULT_TRANSITION"
        }
    }

    fun validateReviewDecision(current: M26AiResultStatus, decision: M26ReviewDecision): String? {
        if (current == M26AiResultStatus.APPROVED && decision == M26ReviewDecision.APPROVED) return null
        if (current == M26AiResultStatus.REJECTED && decision == M26ReviewDecision.REJECTED) return null
        if (current != M26AiResultStatus.PENDING_REVIEW) return "M26_INVALID_RESULT_TRANSITION"
        return null
    }

    private fun M26AiJobStatus.isTerminal(): Boolean =
        this in setOf(M26AiJobStatus.COMPLETED, M26AiJobStatus.FAILED, M26AiJobStatus.CANCELLED, M26AiJobStatus.EXPIRED)

    private fun M26AiResultStatus.isTerminal(): Boolean =
        this in setOf(M26AiResultStatus.APPROVED, M26AiResultStatus.REJECTED, M26AiResultStatus.ARCHIVED)
}

object M26AiOperationsService {
    fun canonicalDuplicateKey(labelA: String, labelB: String): String {
        val pair = listOf(labelA.trim().lowercase(), labelB.trim().lowercase()).sorted()
        return "${pair[0]}|${pair[1]}"
    }

    fun isPublicResult(status: M26AiResultStatus): Boolean = status == M26AiResultStatus.APPROVED

    fun stubModelVersion(): Pair<String, String> = "leover-stub" to "1.0.0"
}
