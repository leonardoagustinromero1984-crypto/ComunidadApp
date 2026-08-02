package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21RatingDistribution
import com.comunidapp.app.data.model.M21ReputationBreakdown
import com.comunidapp.app.data.model.M21Review
import com.comunidapp.app.data.model.M21ReviewResponse
import com.comunidapp.app.data.model.M21ReviewResponseStatus
import com.comunidapp.app.data.model.M21ReviewStatus
import com.comunidapp.app.data.model.M21ReviewSubjectReference
import kotlin.math.round

object M21ReputationAggregator {

    private val countableStatuses = setOf(
        M21ReviewStatus.PUBLISHED,
        M21ReviewStatus.EDITED,
        M21ReviewStatus.DISPUTED,
        M21ReviewStatus.APPEALED
    )

    fun isCountable(review: M21Review): Boolean =
        review.status in countableStatuses && review.contextReference != null

    fun filterCountable(reviews: List<M21Review>): List<M21Review> =
        reviews.filter(::isCountable)

    fun averageRating(reviews: List<M21Review>): Double? {
        val countable = filterCountable(reviews)
        if (countable.isEmpty()) return null
        val avg = countable.map { it.rating.coerceIn(1, 5) }.average()
        return round(avg * 10.0) / 10.0
    }

    fun ratingDistribution(reviews: List<M21Review>): M21RatingDistribution {
        val countable = filterCountable(reviews)
        return M21RatingDistribution(
            oneStar = countable.count { it.rating == 1 },
            twoStars = countable.count { it.rating == 2 },
            threeStars = countable.count { it.rating == 3 },
            fourStars = countable.count { it.rating == 4 },
            fiveStars = countable.count { it.rating == 5 }
        )
    }

    fun reviewsWithResponseCount(reviews: List<M21Review>): Int =
        filterCountable(reviews).count { it.hasResponse }

    fun lastReviewAt(reviews: List<M21Review>): Long? =
        filterCountable(reviews).maxOfOrNull { it.createdAt }

    fun buildBreakdown(
        subject: M21ReviewSubjectReference,
        reviews: List<M21Review>,
        publicReviews: List<M21PublicReview>
    ): M21ReputationBreakdown {
        val countable = filterCountable(reviews)
        return M21ReputationBreakdown(
            subject = subject,
            averageRating = averageRating(reviews),
            publishedReviewCount = countable.size,
            ratingDistribution = ratingDistribution(reviews),
            reviewsWithResponseCount = reviewsWithResponseCount(reviews),
            lastReviewAt = lastReviewAt(reviews),
            reviews = publicReviews
        )
    }

    fun activeResponse(responses: List<M21ReviewResponse>, reviewId: String): M21ReviewResponse? =
        responses.filter { it.reviewId == reviewId }
            .firstOrNull { it.status == M21ReviewResponseStatus.PUBLISHED || it.status == M21ReviewResponseStatus.EDITED }
}
