package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M21ReviewContextReference
import com.comunidapp.app.data.model.M21ReviewContextType
import com.comunidapp.app.data.model.M21ReviewSubjectReference
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingStatus

/** Signals M21 eligibility from terminal booking states without creating reviews. */
object M23BookingReviewEligibilityAdapter {
    fun contextFor(booking: M23Booking, offeringName: String): M21ReviewContextReference? = when (booking.status) {
        M23BookingStatus.COMPLETED -> M21ReviewContextReference(
            contextType = M21ReviewContextType.SERVICE_COMPLETED,
            contextId = booking.id,
            publicLabel = offeringName
        )
        M23BookingStatus.CANCELLED_BY_CUSTOMER,
        M23BookingStatus.CANCELLED_BY_PROVIDER,
        M23BookingStatus.REJECTED,
        M23BookingStatus.EXPIRED -> null
        M23BookingStatus.NO_SHOW -> null
        else -> null
    }

    fun isReviewEligible(booking: M23Booking): Boolean = booking.status == M23BookingStatus.COMPLETED

    fun subjectFor(booking: M23Booking, providerDisplayName: String): M21ReviewSubjectReference =
        M21ReviewSubjectReference(M21ReviewTargetType.SERVICE, booking.providerId, providerDisplayName)
}
