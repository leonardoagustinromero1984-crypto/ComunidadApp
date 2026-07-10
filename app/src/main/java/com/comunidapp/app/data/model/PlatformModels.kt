package com.comunidapp.app.data.model

enum class ReportTargetType {
    POST,
    USER,
    COMMENT;

    companion object {
        fun fromString(value: String?): ReportTargetType =
            entries.find { it.name == value } ?: POST
    }
}

enum class ReportStatus {
    OPEN,
    REVIEWED,
    DISMISSED,
    ACTIONED;

    companion object {
        fun fromString(value: String?): ReportStatus =
            entries.find { it.name == value } ?: OPEN
    }
}

data class ContentReport(
    val id: String,
    val reporterId: String,
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: String,
    val details: String? = null,
    val status: ReportStatus = ReportStatus.OPEN,
    val createdAt: Long? = null
)

data class LostFoundSighting(
    val id: String,
    val postId: String,
    val reporterId: String,
    val reporterName: String,
    val note: String,
    val locationText: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long? = null
)

enum class NotificationType {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    CHAT_MESSAGE,
    ADOPTION_REQUEST,
    FOSTER_REQUEST,
    BOOKING,
    SIGHTING,
    SYSTEM;

    companion object {
        fun fromString(value: String?): NotificationType =
            entries.find { it.name == value } ?: SYSTEM
    }
}

data class AppNotification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedId: String? = null,
    val relatedType: String? = null,
    val readAt: Long? = null,
    val createdAt: Long? = null
) {
    val isUnread: Boolean get() = readAt == null
}

data class ServiceReview(
    val id: String,
    val serviceId: String,
    val authorId: String,
    val authorName: String,
    val rating: Int,
    val comment: String = "",
    val createdAt: Long? = null
)

data class ShopProduct(
    val id: String,
    val ownerId: String,
    val serviceId: String? = null,
    val name: String,
    val description: String = "",
    val price: Double = 0.0,
    val stock: Int = 0,
    val photoUrl: String? = null,
    val active: Boolean = true
)

enum class PaymentIntentStatus {
    CREATED,
    PENDING,
    PAID,
    FAILED,
    CANCELLED;

    companion object {
        fun fromString(value: String?): PaymentIntentStatus =
            entries.find { it.name == value } ?: CREATED
    }
}

data class PaymentIntent(
    val id: String,
    val bookingId: String? = null,
    val payerId: String,
    val providerId: String,
    val amount: Double,
    val currency: String = "ARS",
    val status: PaymentIntentStatus = PaymentIntentStatus.CREATED,
    val provider: String = "MANUAL",
    val externalRef: String? = null,
    val createdAt: Long? = null
)

enum class InterviewStatus {
    NONE,
    SCHEDULED,
    DONE,
    NO_SHOW;

    companion object {
        fun fromString(value: String?): InterviewStatus =
            entries.find { it.name == value } ?: NONE
    }
}

data class AdoptionMatch(
    val id: String,
    val adoptionId: String,
    val userId: String,
    val score: Double,
    val reasons: List<String> = emptyList()
)

enum class ClinicalRecordType {
    NOTE,
    VACCINE,
    SURGERY,
    LAB,
    VISIT;

    companion object {
        fun fromString(value: String?): ClinicalRecordType =
            entries.find { it.name == value } ?: NOTE
    }
}

data class PetClinicalRecord(
    val id: String,
    val petId: String,
    val authorId: String,
    val authorName: String,
    val recordType: ClinicalRecordType = ClinicalRecordType.NOTE,
    val title: String,
    val notes: String = "",
    val recordedAt: Long? = null
)
