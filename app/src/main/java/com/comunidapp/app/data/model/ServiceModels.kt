package com.comunidapp.app.data.model

enum class ServiceCategory {
    VET,
    TRAINER,
    WALKER,
    SHOP,
    DAYCARE,
    GROOMING,
    CAREGIVER,
    PET_FRIENDLY;

    fun toCommunityCategory(): CommunityCategory = when (this) {
        VET -> CommunityCategory.VET
        TRAINER -> CommunityCategory.TRAINER
        WALKER -> CommunityCategory.WALKER
        SHOP -> CommunityCategory.SHOP
        DAYCARE -> CommunityCategory.DAYCARE
        GROOMING -> CommunityCategory.GROOMING
        CAREGIVER -> CommunityCategory.CAREGIVER
        PET_FRIENDLY -> CommunityCategory.PET_FRIENDLY
    }

    companion object {
        @Deprecated("AccountType is LEGACY. Never derive service category from account type.")
        fun fromAccountType(accountType: AccountType): ServiceCategory? {
            @Suppress("UNUSED_PARAMETER")
            val ignored = accountType
            return null
        }

        fun fromCommunityCategory(category: CommunityCategory): ServiceCategory? = when (category) {
            CommunityCategory.VET -> VET
            CommunityCategory.TRAINER -> TRAINER
            CommunityCategory.WALKER -> WALKER
            CommunityCategory.SHOP -> SHOP
            CommunityCategory.DAYCARE -> DAYCARE
            CommunityCategory.GROOMING -> GROOMING
            CommunityCategory.CAREGIVER -> CAREGIVER
            CommunityCategory.PET_FRIENDLY -> PET_FRIENDLY
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
    val slotIntervalMinutes: Int = 30,
    val active: Boolean = true,
    val provinceId: String? = null,
    val localityId: String? = null,
    val localityIds: List<String> = emptyList(),
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val distanceKm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geoIsPublicPremises: Boolean = false,
    val weeklyHours: List<com.comunidapp.app.domain.schedule.WeeklyHoursDay> = emptyList()
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
