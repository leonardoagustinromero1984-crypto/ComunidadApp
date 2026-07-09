package com.comunidapp.app.data.model

data class PostComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdAt: Long? = null
)

enum class AdoptionRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    companion object {
        fun fromString(value: String?): AdoptionRequestStatus =
            entries.find { it.name == value } ?: PENDING
    }
}

data class AdoptionRequest(
    val id: String,
    val adoptionId: String,
    val applicantId: String,
    val applicantName: String,
    val message: String,
    val phone: String? = null,
    val status: AdoptionRequestStatus = AdoptionRequestStatus.PENDING,
    val createdAt: Long? = null
)

enum class BadgeType(val displayName: String) {
    ADOPTION_CHAMPION("Más de 100 adopciones"),
    SOLIDARY_HOME("Hogar Solidario"),
    FREQUENT_DONOR("Donante Frecuente"),
    VERIFIED_WALKER("Paseador Verificado"),
    RECOMMENDED_VET("Veterinaria Recomendada"),
    OUTSTANDING_EDUCATOR("Educador Destacado"),
    COMMUNITY_LEADER("Referente de la Comunidad");

    companion object {
        fun fromString(value: String?): BadgeType? =
            entries.find { it.name == value }
    }
}

data class UserBadge(
    val id: String,
    val userId: String,
    val badgeType: BadgeType,
    val earnedAt: Long? = null
)

enum class DonationType {
    MONEY,
    FOOD,
    MEDICINE,
    SUPPLIES,
    TRANSPORT,
    TIME,
    VOLUNTEER,
    FOSTER;

    companion object {
        fun fromString(value: String?): DonationType =
            entries.find { it.name == value } ?: MONEY
    }
}

data class DonationCampaign(
    val id: String,
    val organizerId: String,
    val title: String,
    val description: String,
    val location: String,
    val goalAmount: Double? = null,
    val raisedAmount: Double = 0.0,
    val donationType: DonationType = DonationType.MONEY,
    val photoUrl: String? = null,
    val active: Boolean = true,
    val createdAt: Long? = null
)
