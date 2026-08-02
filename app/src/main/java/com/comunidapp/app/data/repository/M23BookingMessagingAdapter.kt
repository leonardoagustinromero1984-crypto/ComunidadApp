package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM20DirectConversationInput
import com.comunidapp.app.data.model.M20ContextReferenceType
import com.comunidapp.app.data.model.M20ContextSnapshot
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.domain.m23.M23PrivacySanitizer

/** Opens or reuses an M20 conversation for a booking without exposing reservation details in messages. */
fun interface M23BookingMessagingAdapter {
    suspend fun openConversationForBooking(booking: M23Booking, peerUserId: String, peerDisplayName: String): Result<String>
}

class M23BookingMessagingAdapterImpl(
    private val messaging: M20MessagingRepository,
    private val available: () -> Boolean = { true }
) : M23BookingMessagingAdapter {
    override suspend fun openConversationForBooking(
        booking: M23Booking,
        peerUserId: String,
        peerDisplayName: String
    ): Result<String> {
        if (!available()) return Result.failure(M23BookingException("M23_MESSAGING_UNAVAILABLE"))
        val label = M23PrivacySanitizer.scrubPublicText("Reserva · $peerDisplayName")
        val context = M20ContextSnapshot(
            type = M20ContextReferenceType.BOOKING,
            targetId = booking.id,
            displayLabel = label,
            isPublic = false
        )
        return messaging.createDirectConversation(
            CreateM20DirectConversationInput(peerUserId = peerUserId, context = context)
        ).map { it.id }
    }
}

object UnavailableM23BookingMessagingAdapter : M23BookingMessagingAdapter {
    override suspend fun openConversationForBooking(
        booking: M23Booking,
        peerUserId: String,
        peerDisplayName: String
    ): Result<String> = Result.failure(M23BookingException("M23_MESSAGING_UNAVAILABLE"))
}
