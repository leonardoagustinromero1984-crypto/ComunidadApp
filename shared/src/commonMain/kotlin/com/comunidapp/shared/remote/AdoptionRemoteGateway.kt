package com.comunidapp.shared.remote

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class RemoteCreateAdoptionParams(
    val petId: String,
    val title: String,
    val description: String,
    val requirements: String = "",
    val locationText: String = "",
    val publish: Boolean = true
)

internal interface AdoptionRemoteGateway {
    suspend fun listPublished(): Result<List<RemoteAdoptionPublicationRow>>
    suspend fun fetchById(adoptionId: String): Result<RemoteAdoptionPublicationRow?>
    suspend fun create(params: RemoteCreateAdoptionParams): Result<RemoteAdoptionPublicationRow>
}

internal class SupabaseAdoptionRemoteGateway(
    private val client: SupabaseClient
) : AdoptionRemoteGateway {

    override suspend fun listPublished(): Result<List<RemoteAdoptionPublicationRow>> {
        return try {
            val rows = client.postgrest.rpc(
                function = "m09_list_published_adoptions"
            ).decodeList<RemoteAdoptionPublicationRow>()
            Result.success(rows)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun fetchById(adoptionId: String): Result<RemoteAdoptionPublicationRow?> {
        return try {
            val rows = client.postgrest.rpc(
                function = "m09_get_adoption",
                parameters = buildJsonObject {
                    put("p_adoption_id", adoptionId)
                }
            ).decodeList<RemoteAdoptionPublicationRow>()
            Result.success(rows.firstOrNull())
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun create(params: RemoteCreateAdoptionParams): Result<RemoteAdoptionPublicationRow> {
        return try {
            val rows = client.postgrest.rpc(
                function = "m09_create_adoption_publication",
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_title", params.title)
                    put("p_description", params.description)
                    put("p_requirements", params.requirements)
                    put("p_location_text", params.locationText)
                    put("p_publish", params.publish)
                }
            ).decodeList<RemoteAdoptionPublicationRow>()
            val row = rows.firstOrNull()
                ?: return Result.failure(IllegalStateException("ADOPTION_CREATE_EMPTY"))
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

internal class FakeAdoptionRemoteGateway(
    var list: List<RemoteAdoptionPublicationRow> = emptyList(),
    var detail: RemoteAdoptionPublicationRow? = null,
    var listError: Throwable? = null,
    var detailError: Throwable? = null,
    var createError: Throwable? = null,
    var created: RemoteAdoptionPublicationRow? = null,
    var listCalls: Int = 0,
    var createCalls: Int = 0,
    var lastCreate: RemoteCreateAdoptionParams? = null
) : AdoptionRemoteGateway {
    override suspend fun listPublished(): Result<List<RemoteAdoptionPublicationRow>> {
        listCalls++
        listError?.let { return Result.failure(it) }
        return Result.success(list)
    }

    override suspend fun fetchById(adoptionId: String): Result<RemoteAdoptionPublicationRow?> {
        detailError?.let { return Result.failure(it) }
        val row = detail?.takeIf { it.id == adoptionId }
            ?: list.firstOrNull { it.id == adoptionId }
            ?: created?.takeIf { it.id == adoptionId }
        return Result.success(row)
    }

    override suspend fun create(params: RemoteCreateAdoptionParams): Result<RemoteAdoptionPublicationRow> {
        createCalls++
        lastCreate = params
        createError?.let { return Result.failure(it) }
        val row = created ?: RemoteAdoptionPublicationRow(
            id = "adopt-new-1",
            name = params.title,
            title = params.title,
            description = params.description,
            requirements = params.requirements,
            location = params.locationText,
            locationText = params.locationText,
            status = if (params.publish) "PUBLISHED" else "DRAFT",
            petId = params.petId
        )
        created = row
        list = list + row
        return Result.success(row)
    }
}

@Serializable
internal data class RemoteAdoptionApplicationRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("applicant_user_id") val applicantUserId: String? = null,
    @SerialName("applicant_name") val applicantName: String? = null,
    val message: String = "",
    @SerialName("housing_type") val housingType: String? = null,
    @SerialName("has_other_pets") val hasOtherPets: Boolean? = null,
    @SerialName("previous_experience") val previousExperience: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    val status: String = "SUBMITTED",
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("adoption_title") val adoptionTitle: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("pet_photo_url") val petPhotoUrl: String? = null
)

internal data class RemoteSubmitApplicationParams(
    val adoptionId: String,
    val message: String,
    val housingType: String? = null,
    val hasOtherPets: Boolean? = null,
    val previousExperience: String? = null,
    val contactPhone: String? = null
)

internal interface AdoptionApplicationRemoteGateway {
    suspend fun submit(params: RemoteSubmitApplicationParams): Result<RemoteAdoptionApplicationRow>
    suspend fun withdraw(applicationId: String): Result<RemoteAdoptionApplicationRow>
    suspend fun listMine(): Result<List<RemoteAdoptionApplicationRow>>
    suspend fun listReceived(status: String?): Result<List<RemoteAdoptionApplicationRow>>
    suspend fun getApplication(id: String): Result<RemoteAdoptionApplicationRow>
    suspend fun markUnderReview(id: String): Result<RemoteAdoptionApplicationRow>
    suspend fun accept(id: String): Result<RemoteAdoptionApplicationRow>
    suspend fun reject(id: String, reason: String?): Result<RemoteAdoptionApplicationRow>
}

internal class SupabaseAdoptionApplicationRemoteGateway(
    private val client: SupabaseClient
) : AdoptionApplicationRemoteGateway {

    override suspend fun submit(params: RemoteSubmitApplicationParams): Result<RemoteAdoptionApplicationRow> =
        try {
            val rows = client.postgrest.rpc(
                function = "m09_submit_application",
                parameters = buildJsonObject {
                    put("p_adoption_id", params.adoptionId)
                    put("p_message", params.message)
                    params.housingType?.let { put("p_housing_type", it) }
                        ?: put("p_housing_type", JsonNull)
                    params.hasOtherPets?.let { put("p_has_other_pets", it) }
                        ?: put("p_has_other_pets", JsonNull)
                    params.previousExperience?.let { put("p_previous_experience", it) }
                        ?: put("p_previous_experience", JsonNull)
                    params.contactPhone?.let { put("p_contact_phone", it) }
                        ?: put("p_contact_phone", JsonNull)
                }
            ).decodeList<RemoteAdoptionApplicationRow>()
            val row = rows.firstOrNull()
                ?: return Result.failure(IllegalStateException("APPLICATION_SUBMIT_EMPTY"))
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }

    override suspend fun withdraw(applicationId: String): Result<RemoteAdoptionApplicationRow> =
        try {
            val rows = client.postgrest.rpc(
                function = "m09_withdraw_application",
                parameters = buildJsonObject { put("p_application_id", applicationId) }
            ).decodeList<RemoteAdoptionApplicationRow>()
            val row = rows.firstOrNull()
                ?: return Result.failure(IllegalStateException("APPLICATION_WITHDRAW_EMPTY"))
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }

    override suspend fun listMine(): Result<List<RemoteAdoptionApplicationRow>> =
        try {
            Result.success(
                client.postgrest.rpc(function = "m09_list_my_applications")
                    .decodeList()
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }

    override suspend fun listReceived(status: String?): Result<List<RemoteAdoptionApplicationRow>> =
        try {
            Result.success(
                client.postgrest.rpc(
                    function = "m09_list_received_applications",
                    parameters = buildJsonObject {
                        status?.let { put("p_status", it) } ?: put("p_status", JsonNull)
                    }
                ).decodeList()
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }

    override suspend fun getApplication(id: String): Result<RemoteAdoptionApplicationRow> =
        try {
            val rows = client.postgrest.rpc(
                function = "m09_get_application",
                parameters = buildJsonObject { put("p_application_id", id) }
            ).decodeList<RemoteAdoptionApplicationRow>()
            val row = rows.firstOrNull()
                ?: return Result.failure(IllegalStateException("APPLICATION_NOT_FOUND"))
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }

    override suspend fun markUnderReview(id: String): Result<RemoteAdoptionApplicationRow> =
        try {
            val rows = client.postgrest.rpc(
                function = "m09_mark_application_under_review",
                parameters = buildJsonObject { put("p_application_id", id) }
            ).decodeList<RemoteAdoptionApplicationRow>()
            val row = rows.firstOrNull()
                ?: return Result.failure(IllegalStateException("APPLICATION_MARK_EMPTY"))
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }

    override suspend fun accept(id: String): Result<RemoteAdoptionApplicationRow> =
        try {
            val rows = client.postgrest.rpc(
                function = "m09_accept_application",
                parameters = buildJsonObject { put("p_application_id", id) }
            ).decodeList<RemoteAdoptionApplicationRow>()
            val row = rows.firstOrNull()
                ?: return Result.failure(IllegalStateException("APPLICATION_ACCEPT_EMPTY"))
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }

    override suspend fun reject(id: String, reason: String?): Result<RemoteAdoptionApplicationRow> =
        try {
            val rows = client.postgrest.rpc(
                function = "m09_reject_application",
                parameters = buildJsonObject {
                    put("p_application_id", id)
                    reason?.let { put("p_rejection_reason", it) }
                        ?: put("p_rejection_reason", JsonNull)
                }
            ).decodeList<RemoteAdoptionApplicationRow>()
            val row = rows.firstOrNull()
                ?: return Result.failure(IllegalStateException("APPLICATION_REJECT_EMPTY"))
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }
}

internal class FakeAdoptionApplicationRemoteGateway(
    var mine: List<RemoteAdoptionApplicationRow> = emptyList(),
    var received: List<RemoteAdoptionApplicationRow> = emptyList(),
    var submitError: Throwable? = null,
    var withdrawError: Throwable? = null,
    var listError: Throwable? = null,
    var receivedError: Throwable? = null,
    var getError: Throwable? = null,
    var reviewError: Throwable? = null,
    var submitCalls: Int = 0,
    var lastSubmit: RemoteSubmitApplicationParams? = null
) : AdoptionApplicationRemoteGateway {
    override suspend fun submit(params: RemoteSubmitApplicationParams): Result<RemoteAdoptionApplicationRow> {
        submitCalls++
        lastSubmit = params
        submitError?.let { return Result.failure(it) }
        val row = RemoteAdoptionApplicationRow(
            id = "app-${submitCalls}",
            adoptionId = params.adoptionId,
            message = params.message,
            status = "SUBMITTED",
            adoptionTitle = "Adopción",
            petName = "Mascota",
            submittedAt = "2026-08-13T12:00:00Z"
        )
        mine = mine + row
        return Result.success(row)
    }

    override suspend fun withdraw(applicationId: String): Result<RemoteAdoptionApplicationRow> {
        withdrawError?.let { return Result.failure(it) }
        val existing = mine.firstOrNull { it.id == applicationId }
            ?: return Result.failure(IllegalStateException("APPLICATION_NOT_FOUND"))
        val updated = existing.copy(status = "WITHDRAWN")
        mine = mine.map { if (it.id == applicationId) updated else it }
        received = received.map { if (it.id == applicationId) updated else it }
        return Result.success(updated)
    }

    override suspend fun listMine(): Result<List<RemoteAdoptionApplicationRow>> {
        listError?.let { return Result.failure(it) }
        return Result.success(mine)
    }

    override suspend fun listReceived(status: String?): Result<List<RemoteAdoptionApplicationRow>> {
        receivedError?.let { return Result.failure(it) }
        val filtered = if (status.isNullOrBlank()) received
        else received.filter { it.status.equals(status, ignoreCase = true) }
        return Result.success(filtered)
    }

    override suspend fun getApplication(id: String): Result<RemoteAdoptionApplicationRow> {
        getError?.let { return Result.failure(it) }
        val row = received.firstOrNull { it.id == id }
            ?: mine.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalStateException("APPLICATION_NOT_FOUND"))
        return Result.success(row)
    }

    override suspend fun markUnderReview(id: String): Result<RemoteAdoptionApplicationRow> {
        reviewError?.let { return Result.failure(it) }
        return transition(id, "UNDER_REVIEW") { current ->
            when (current) {
                "UNDER_REVIEW" -> current
                "SUBMITTED" -> "UNDER_REVIEW"
                else -> null
            }
        }
    }

    override suspend fun accept(id: String): Result<RemoteAdoptionApplicationRow> {
        reviewError?.let { return Result.failure(it) }
        return transition(id, "ACCEPTED") { current ->
            when (current) {
                "ACCEPTED" -> current
                "SUBMITTED", "UNDER_REVIEW" -> "ACCEPTED"
                else -> null
            }
        }
    }

    override suspend fun reject(id: String, reason: String?): Result<RemoteAdoptionApplicationRow> {
        reviewError?.let { return Result.failure(it) }
        return transition(id, "REJECTED", reason) { current ->
            when (current) {
                "REJECTED" -> current
                "SUBMITTED", "UNDER_REVIEW" -> "REJECTED"
                else -> null
            }
        }
    }

    private fun transition(
        id: String,
        fallbackStatus: String,
        rejectionReason: String? = null,
        nextStatus: (String) -> String?
    ): Result<RemoteAdoptionApplicationRow> {
        val existing = received.firstOrNull { it.id == id }
            ?: mine.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalStateException("APPLICATION_NOT_FOUND"))
        val target = nextStatus(existing.status)
            ?: return Result.failure(IllegalStateException("APPLICATION_INVALID_TRANSITION"))
        val updated = existing.copy(
            status = target.ifBlank { fallbackStatus },
            rejectionReason = rejectionReason ?: existing.rejectionReason
        )
        received = received.map { if (it.id == id) updated else it }
        mine = mine.map { if (it.id == id) updated else it }
        if (received.none { it.id == id }) {
            received = received + updated
        }
        return Result.success(updated)
    }
}

internal fun mapAdoptionThrowable(t: Throwable): String {
    val code = t.message.orEmpty()
    val raw = code.lowercase()
    return when {
        "ADOPTION_ALREADY_EXISTS" in code || "already_exists" in raw && "adoption" in raw ->
            "Ya existe una publicación abierta para esa mascota."
        "PET_NOT_ADOPTABLE" in code || "pet_not_adoptable" in raw ->
            "Esa mascota no está disponible para adopción."
        "ADOPTION_TITLE_REQUIRED" in code ->
            "El título es obligatorio."
        "ADOPTION_DESCRIPTION_REQUIRED" in code ->
            "La descripción es obligatoria."
        "APPLICATION_ALREADY_EXISTS" in code ->
            "Ya tenés una solicitud activa para esta adopción."
        "APPLICATION_MESSAGE" in code ->
            "El mensaje de postulación no es válido."
        "APPLICATION_FORBIDDEN" in code ->
            "No tenés permiso para esta acción."
        "APPLICATION_INVALID_TRANSITION" in code ->
            "Esa transición de estado no está permitida."
        "APPLICATION_NOT_FOUND" in code ->
            "No encontramos ese contenido."
        "401" in raw || "jwt" in raw || "not authenticated" in raw || "session" in raw ||
            "not_authenticated" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "permission" in raw || "rls" in raw || "policy" in raw ||
            "forbidden" in raw ->
            "No tenés permiso para esta acción."
        "404" in raw || "not found" in raw || "ADOPTION_NOT_FOUND" in code ->
            "No encontramos ese contenido."
        "network" in raw || "timeout" in raw || "unable to resolve" in raw || "connection" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}

internal fun classifyAdoptionWrite(t: Throwable): AdoptionWriteKind {
    val code = t.message.orEmpty()
    val raw = code.lowercase()
    return when {
        "401" in raw || "not_authenticated" in raw || "jwt" in raw -> AdoptionWriteKind.UNAUTHENTICATED
        "APPLICATION_FORBIDDEN" in code || "403" in raw || "forbidden" in raw || "rls" in raw ->
            AdoptionWriteKind.FORBIDDEN
        "ADOPTION_ALREADY_EXISTS" in code || "APPLICATION_ALREADY_EXISTS" in code ->
            AdoptionWriteKind.CONFLICT
        "ADOPTION_TITLE_REQUIRED" in code || "ADOPTION_DESCRIPTION_REQUIRED" in code ||
            "ADOPTION_PET_REQUIRED" in code || "APPLICATION_MESSAGE" in code ||
            "APPLICATION_INVALID_TRANSITION" in code ->
            AdoptionWriteKind.VALIDATION
        else -> AdoptionWriteKind.BACKEND
    }
}

internal enum class AdoptionWriteKind {
    UNAUTHENTICATED,
    FORBIDDEN,
    CONFLICT,
    VALIDATION,
    BACKEND
}
