package com.comunidapp.app.domain.m26

import com.comunidapp.app.data.model.M26EvaluatedRecommendation
import com.comunidapp.app.data.model.M26PublicRecommendation
import com.comunidapp.app.data.model.M26RecommendationStatus

object M26RecommendationEligibilityService {
    fun isEligibleForDisplay(recommendation: M26EvaluatedRecommendation): Boolean =
        recommendation.humanReviewed && recommendation.status == M26RecommendationStatus.APPROVED

    fun filterEligible(recommendations: List<M26EvaluatedRecommendation>): List<M26EvaluatedRecommendation> =
        recommendations.filter(::isEligibleForDisplay)

    fun filterEligiblePublic(recommendations: List<M26EvaluatedRecommendation>): List<M26PublicRecommendation> =
        filterEligible(recommendations).map { it.toPublic() }
}
