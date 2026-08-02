package com.comunidapp.app.domain.m26

import com.comunidapp.app.data.model.M26AssistanceSession
import com.comunidapp.app.data.model.M26DuplicateCandidate
import com.comunidapp.app.data.model.M26EvaluatedRecommendation
import com.comunidapp.app.data.model.M26PublicAssistanceSession
import com.comunidapp.app.data.model.M26PublicDuplicateCandidate
import com.comunidapp.app.data.model.M26PublicRecommendation
import com.comunidapp.app.data.model.M26PublicVisualMatch
import com.comunidapp.app.data.model.M26RecommendationStatus
import com.comunidapp.app.data.model.M26VisualMatchSuggestion

object M26PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val addressPattern = Regex("(?i)(calle|av\\.?|avenida|pasaje)\\s+[\\w\\s\\d]+")
    private val documentPattern = Regex("(?i)(dni|cuil|cuit|documento)\\s*[:#]?\\s*[\\w\\d-]+")
    private val userIdPattern = Regex("(?i)(user[_-]?id|owner[_-]?id|requester)\\s*=\\s*\\S+")

    fun scrubPublicText(text: String): String = text
        .replace(emailPattern, "[redactado]")
        .replace(phonePattern, "[redactado]")
        .replace(addressPattern, "[redactado]")
        .replace(documentPattern, "[redactado]")
        .replace(userIdPattern, "[redactado]")
        .trim()

    fun toPublicVisualMatch(match: M26VisualMatchSuggestion): M26PublicVisualMatch = M26PublicVisualMatch(
        sourceLabel = scrubPublicText(match.sourceLabel),
        targetLabel = scrubPublicText(match.targetLabel),
        score = match.score,
        confidenceBand = match.confidenceBand,
        status = match.status
    )

    fun toPublicDuplicate(candidate: M26DuplicateCandidate): M26PublicDuplicateCandidate =
        M26PublicDuplicateCandidate(
            primaryLabel = scrubPublicText(candidate.primaryLabel),
            duplicateLabel = scrubPublicText(candidate.duplicateLabel),
            similarityScore = candidate.similarityScore,
            status = candidate.status
        )

    fun toPublicAssistance(session: M26AssistanceSession): M26PublicAssistanceSession =
        M26PublicAssistanceSession(
            topic = session.topic,
            status = session.status,
            summary = scrubPublicText(session.summary)
        )

    fun toPublicRecommendation(recommendation: M26EvaluatedRecommendation): M26PublicRecommendation =
        M26PublicRecommendation(
            title = scrubPublicText(recommendation.title),
            rationale = scrubPublicText(recommendation.rationale),
            kind = recommendation.kind,
            humanReviewed = recommendation.humanReviewed,
            approvedForDisplay = M26RecommendationEligibilityService.isEligibleForDisplay(recommendation)
        )
}
