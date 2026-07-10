package com.comunidapp.app.data.model

enum class ServiceCategory {
    VET,
    TRAINER,
    WALKER,
    SHOP;

    fun toCommunityCategory(): CommunityCategory = when (this) {
        VET -> CommunityCategory.VET
        TRAINER -> CommunityCategory.TRAINER
        WALKER -> CommunityCategory.WALKER
        SHOP -> CommunityCategory.SHOP
    }

    companion object {
        fun fromAccountType(accountType: AccountType): ServiceCategory? = when (accountType) {
            AccountType.VET -> VET
            AccountType.TRAINER -> TRAINER
            AccountType.WALKER -> WALKER
            AccountType.SHOP -> SHOP
            else -> null
        }

        fun fromCommunityCategory(category: CommunityCategory): ServiceCategory? = when (category) {
            CommunityCategory.VET -> VET
            CommunityCategory.TRAINER -> TRAINER
            CommunityCategory.WALKER -> WALKER
            CommunityCategory.SHOP -> SHOP
            else -> null
        }

        fun fromString(value: String?): ServiceCategory =
            entries.find { it.name == value } ?: VET
    }
}

data class ServiceProfile(
    val id: String,
    val ownerId: String,
    val category: ServiceCategory,
    val name: String,
    val location: String,
    val description: String = "",
    val contactInfo: String? = null,
    val photoUrl: String? = null,
    val tags: List<String> = emptyList(),
    val scheduleText: String? = null,
    val priceFrom: Double? = null,
    val acceptsBookings: Boolean = true,
    val active: Boolean = true
) {
    fun toCommunityListing(): CommunityListing = CommunityListing(
        id = id,
        category = category.toCommunityCategory(),
        name = name,
        photoUrl = photoUrl,
        location = location,
        description = description,
        contactInfo = contactInfo,
        tags = tags
    )
}

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED;

    companion object {
        fun fromString(value: String?): BookingStatus =
            entries.find { it.name == value } ?: PENDING
    }
}

enum class PaymentStatus {
    UNPAID,
    PENDING_TRANSFER,
    PAID_CASH,
    PAID_TRANSFER,
    WAIVED;

    companion object {
        fun fromString(value: String?): PaymentStatus =
            entries.find { it.name == value } ?: UNPAID
    }
}

data class ServiceBooking(
    val id: String,
    val serviceId: String,
    val providerId: String,
    val clientId: String,
    val clientName: String,
    val scheduledAt: Long,
    val notes: String = "",
    val status: BookingStatus = BookingStatus.PENDING,
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    val paymentMethod: String? = null,
    val amount: Double? = null,
    val createdAt: Long? = null
)

enum class FosterRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    companion object {
        fun fromString(value: String?): FosterRequestStatus =
            entries.find { it.name == value } ?: PENDING
    }
}

data class FosterRequest(
    val id: String,
    val fosterHomeId: String,
    val applicantId: String,
    val applicantName: String,
    val message: String,
    val phone: String? = null,
    val status: FosterRequestStatus = FosterRequestStatus.PENDING,
    val createdAt: Long? = null
)
