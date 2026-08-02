package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23PublicBooking
import com.comunidapp.app.data.model.M23PublicBookingContext

/** Keeps customer identity, notes and internal references out of public M23 projections. */
object M23PrivacySanitizer {
    private val email = Regex("""[\w.+-]+@[\w.-]+\.\w+""")
    private val phone = Regex("""(?<!\w)(?:\+?\d[\d\s().-]{7,}\d)""")

    fun scrubPublicText(value: String): String = value.replace(email, "[contacto oculto]").replace(phone, "[contacto oculto]")

    fun publicBooking(booking: M23Booking): M23PublicBooking =
        M23PublicBooking(booking.startsAt, booking.endsAt, booking.zoneId, booking.modality, booking.status)

    fun publicContext(booking: M23Booking, providerName: String, offeringName: String): M23PublicBookingContext =
        M23PublicBookingContext(
            providerDisplayName = scrubPublicText(providerName),
            offeringName = scrubPublicText(offeringName),
            startsAt = booking.startsAt,
            endsAt = booking.endsAt,
            zoneId = booking.zoneId,
            modality = booking.modality,
            status = booking.status
        )
}
