package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DaycareReservationRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val status: String? = null
)

@Serializable
data class DaycareGuestRow(
    val id: String,
    @SerialName("booking_id") val bookingId: String? = null,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("planned_checkout_at") val plannedCheckoutAt: String? = null,
    val status: String? = null
)

class CanonicalDaycareRepository {
    suspend fun listReservations(): Result<List<DaycareReservationRow>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(CanonicalBackend.RPC_LIST_DAYCARE_RESERVATIONS).decodeAs()
        M08RpcDecoding.decodeRows(element)
    }

    suspend fun listGuests(): Result<List<DaycareGuestRow>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(CanonicalBackend.RPC_LIST_DAYCARE_GUESTS).decodeAs()
        M08RpcDecoding.decodeRows(element)
    }
}
