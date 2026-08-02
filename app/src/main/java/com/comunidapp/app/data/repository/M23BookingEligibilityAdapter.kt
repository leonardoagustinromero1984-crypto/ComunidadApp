package com.comunidapp.app.data.repository

/** Boundary stub: M21's authoritative COMPLETED signal will be wired in a later M23 block. */
fun interface M23BookingEligibilityAdapter {
    suspend fun isEligible(customerUserId: String, providerId: String): Boolean
}

object AllowAllM23BookingEligibilityAdapter : M23BookingEligibilityAdapter {
    override suspend fun isEligible(customerUserId: String, providerId: String): Boolean = true
}
