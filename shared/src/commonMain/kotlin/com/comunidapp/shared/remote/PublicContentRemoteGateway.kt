package com.comunidapp.shared.remote

import com.comunidapp.shared.media.MediaRef
import com.comunidapp.shared.media.MediaRefParser
import com.comunidapp.shared.publiccontent.PublicContent
import com.comunidapp.shared.publiccontent.PublicLostFoundCaseType
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

@Serializable
internal data class PublicPetDto(
    @SerialName("public_code") val publicCode: String,
    @SerialName("page_kind") val pageKind: String? = null,
    @SerialName("display_name") val displayName: String = "",
    val species: String? = null,
    @SerialName("breed_text") val breedText: String? = null,
    val sex: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("distinctive_marks") val distinctiveMarks: String? = null,
    @SerialName("microchip_masked") val microchipMasked: String? = null,
    val status: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
internal data class PublicAdoptionDto(
    @SerialName("public_code") val publicCode: String,
    val title: String? = null,
    val name: String? = null,
    val description: String? = null,
    val requirements: String? = null,
    val species: String? = null,
    val sex: String? = null,
    @SerialName("age_years") val ageYears: Int? = null,
    @SerialName("age_months") val ageMonths: Int? = null,
    val size: String? = null,
    val status: String = "",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("location_text") val locationText: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("publisher_display_name") val publisherDisplayName: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
internal data class PublicLostFoundDto(
    @SerialName("public_code") val publicCode: String,
    @SerialName("case_type") val caseType: String = "",
    @SerialName("pet_name") val petName: String? = null,
    val species: String? = null,
    val description: String? = null,
    @SerialName("zone_text") val zoneText: String? = null,
    val status: String = "",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Resultado de RPC pública: payload OK, NOT_PUBLIC (null), o error.
 */
internal sealed interface PublicRpcOutcome<out T> {
    data class Ok<T>(val value: T) : PublicRpcOutcome<T>
    data object NotPublic : PublicRpcOutcome<Nothing>
    data class Failed(val error: Throwable) : PublicRpcOutcome<Nothing>
}

internal interface PublicContentRemoteGateway {
    suspend fun getPublicPet(publicCode: String): PublicRpcOutcome<PublicPetDto>
    suspend fun getPublicAdoption(publicCode: String): PublicRpcOutcome<PublicAdoptionDto>
    suspend fun getPublicLostCase(publicCode: String): PublicRpcOutcome<PublicLostFoundDto>
    suspend fun getPublicFoundCase(publicCode: String): PublicRpcOutcome<PublicLostFoundDto>
}

internal class SupabasePublicContentRemoteGateway(
    private val client: SupabaseClient
) : PublicContentRemoteGateway {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getPublicPet(publicCode: String): PublicRpcOutcome<PublicPetDto> =
        rpcJson("get_public_pet", publicCode) { el ->
            json.decodeFromJsonElement(PublicPetDto.serializer(), el)
        }

    override suspend fun getPublicAdoption(publicCode: String): PublicRpcOutcome<PublicAdoptionDto> =
        rpcJson("get_public_adoption", publicCode) { el ->
            json.decodeFromJsonElement(PublicAdoptionDto.serializer(), el)
        }

    override suspend fun getPublicLostCase(publicCode: String): PublicRpcOutcome<PublicLostFoundDto> =
        rpcJson("get_public_lost_case", publicCode) { el ->
            json.decodeFromJsonElement(PublicLostFoundDto.serializer(), el)
        }

    override suspend fun getPublicFoundCase(publicCode: String): PublicRpcOutcome<PublicLostFoundDto> =
        rpcJson("get_public_found_case", publicCode) { el ->
            json.decodeFromJsonElement(PublicLostFoundDto.serializer(), el)
        }

    private suspend fun <T> rpcJson(
        function: String,
        publicCode: String,
        decode: (JsonObject) -> T
    ): PublicRpcOutcome<T> = try {
        val element = client.postgrest.rpc(
            function = function,
            parameters = buildJsonObject {
                put("p_public_code", publicCode)
            }
        ).decodeAs<JsonObject>()
        PublicRpcOutcome.Ok(decode(element))
    } catch (t: Throwable) {
        if (isNotPublicRpcError(t)) PublicRpcOutcome.NotPublic
        else PublicRpcOutcome.Failed(t)
    }
}

internal class FakePublicContentRemoteGateway(
    var pet: PublicPetDto? = null,
    var adoption: PublicAdoptionDto? = null,
    var lost: PublicLostFoundDto? = null,
    var found: PublicLostFoundDto? = null,
    var petError: Throwable? = null,
    var adoptionError: Throwable? = null,
    var lostError: Throwable? = null,
    var foundError: Throwable? = null,
    var petCalls: Int = 0,
    var adoptionCalls: Int = 0,
    var lostCalls: Int = 0,
    var foundCalls: Int = 0
) : PublicContentRemoteGateway {
    override suspend fun getPublicPet(publicCode: String): PublicRpcOutcome<PublicPetDto> {
        petCalls++
        petError?.let { return mapError(it) }
        val row = pet?.takeIf { it.publicCode == publicCode }
        return if (row != null) PublicRpcOutcome.Ok(row) else PublicRpcOutcome.NotPublic
    }

    override suspend fun getPublicAdoption(publicCode: String): PublicRpcOutcome<PublicAdoptionDto> {
        adoptionCalls++
        adoptionError?.let { return mapError(it) }
        val row = adoption?.takeIf { it.publicCode == publicCode }
        return if (row != null) PublicRpcOutcome.Ok(row) else PublicRpcOutcome.NotPublic
    }

    override suspend fun getPublicLostCase(publicCode: String): PublicRpcOutcome<PublicLostFoundDto> {
        lostCalls++
        lostError?.let { return mapError(it) }
        val row = lost?.takeIf { it.publicCode == publicCode }
        return if (row != null) PublicRpcOutcome.Ok(row) else PublicRpcOutcome.NotPublic
    }

    override suspend fun getPublicFoundCase(publicCode: String): PublicRpcOutcome<PublicLostFoundDto> {
        foundCalls++
        foundError?.let { return mapError(it) }
        val row = found?.takeIf { it.publicCode == publicCode }
        return if (row != null) PublicRpcOutcome.Ok(row) else PublicRpcOutcome.NotPublic
    }

    private fun mapError(t: Throwable): PublicRpcOutcome<Nothing> =
        if (isNotPublicRpcError(t)) PublicRpcOutcome.NotPublic else PublicRpcOutcome.Failed(t)
}

internal fun isNotPublicRpcError(t: Throwable): Boolean {
    val msg = t.message.orEmpty()
    return "NOT_PUBLIC" in msg ||
        "PUBLIC_PASSPORT_NOT_AVAILABLE" in msg ||
        "P0001" in msg
}

internal fun publicPhotoRef(photoUrl: String?): MediaRef? =
    MediaRefParser.fromPhotoField(photoUrl)

internal fun PublicPetDto.toSafeContent(): PublicContent.Pet =
    PublicContent.Pet(
        publicCode = publicCode,
        displayName = displayName.ifBlank { "Mascota" },
        species = species?.takeIf { it.isNotBlank() },
        breedText = breedText?.takeIf { it.isNotBlank() },
        sex = sex?.takeIf { it.isNotBlank() },
        status = status.ifBlank { "—" },
        photo = publicPhotoRef(photoUrl),
        primaryColor = primaryColor?.takeIf { it.isNotBlank() },
        distinctiveMarks = distinctiveMarks?.takeIf { it.isNotBlank() },
        microchipMasked = microchipMasked?.takeIf { it.isNotBlank() },
        birthDate = birthDate?.takeIf { it.isNotBlank() }
    )

internal fun PublicAdoptionDto.toSafeContent(): PublicContent.Adoption =
    PublicContent.Adoption(
        publicCode = publicCode,
        title = title?.takeIf { it.isNotBlank() },
        name = name?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        species = species?.takeIf { it.isNotBlank() },
        sex = sex?.takeIf { it.isNotBlank() },
        ageYears = ageYears,
        ageMonths = ageMonths,
        size = size?.takeIf { it.isNotBlank() },
        status = status.ifBlank { "—" },
        isActive = isActive,
        locationText = locationText?.takeIf { it.isNotBlank() },
        photo = publicPhotoRef(photoUrl),
        publisherDisplayName = publisherDisplayName?.takeIf { it.isNotBlank() }
    )

internal fun PublicLostFoundDto.toSafeContent(): PublicContent.LostFound? {
    val type = when (caseType.trim().uppercase()) {
        "LOST" -> PublicLostFoundCaseType.LOST
        "FOUND" -> PublicLostFoundCaseType.FOUND
        else -> return null
    }
    return PublicContent.LostFound(
        publicCode = publicCode,
        caseType = type,
        petName = petName?.takeIf { it.isNotBlank() },
        species = species?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        zoneText = zoneText?.takeIf { it.isNotBlank() },
        status = status.ifBlank { "—" },
        isActive = isActive,
        photo = publicPhotoRef(photoUrl)
    )
}

internal fun mapPublicContentThrowable(t: Throwable): String {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "network" in raw || "timeout" in raw || "unable to resolve" in raw ||
            "connection" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}

internal fun isPublicNetworkError(t: Throwable): Boolean {
    val raw = t.message.orEmpty().lowercase()
    return "network" in raw || "timeout" in raw || "unable to resolve" in raw ||
        "connection" in raw
}
