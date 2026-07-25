package com.comunidapp.app.data.remote.supabase.m14

import com.comunidapp.app.data.model.M14Credential
import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicCredentialSummary
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.M14VerificationRequest
import com.comunidapp.app.data.model.M14VerificationRequestStatus
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** LeoVer M14 Bloque 2 — RPC-only transport and JSONB row mappings. */
@Serializable
data class M14PetPassportRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("passport_number") val passportNumber: String,
    @SerialName("public_code") val publicCode: String? = null,
    val status: String = "DRAFT",
    val visibility: String = "PRIVATE",
    @SerialName("display_name") val displayName: String,
    val species: String,
    @SerialName("breed_text") val breedText: String? = null,
    val sex: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("distinctive_marks") val distinctiveMarks: String? = null,
    @SerialName("microchip_masked") val microchipMasked: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("activated_at") val activatedAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null
)

@Serializable
data class M14CredentialRow(
    val id: String,
    @SerialName("passport_id") val passportId: String,
    val type: String = "OTHER",
    val title: String,
    @SerialName("issuer_organization_id") val issuerOrganizationId: String? = null,
    @SerialName("issuer_professional_id") val issuerProfessionalId: String? = null,
    @SerialName("issued_at") val issuedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val status: String = "DRAFT",
    val visibility: String = "PRIVATE",
    @SerialName("media_refs") val mediaRefs: List<String> = emptyList(),
    @SerialName("external_reference_masked") val externalReferenceMasked: String? = null,
    @SerialName("note_private") val notePrivate: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class M14VerificationRequestRow(
    val id: String,
    @SerialName("credential_id") val credentialId: String,
    @SerialName("requested_by") val requestedBy: String? = null,
    @SerialName("target_organization_id") val targetOrganizationId: String? = null,
    @SerialName("target_professional_id") val targetProfessionalId: String? = null,
    val status: String = "PENDING",
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolution_reason") val resolutionReason: String? = null
)

@Serializable
data class M14PublicCredentialRow(
    val type: String = "OTHER",
    val title: String = "",
    @SerialName("issued_at") val issuedAt: String? = null,
    val status: String = ""
)

@Serializable
data class M14PublicPassportProjectionRow(
    @SerialName("public_code") val publicCode: String? = null,
    @SerialName("display_name") val displayName: String,
    val species: String,
    @SerialName("breed_text") val breedText: String? = null,
    val sex: String? = null,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("distinctive_marks") val distinctiveMarks: String? = null,
    val status: String = "ACTIVE",
    @SerialName("microchip_masked") val microchipMasked: String? = null,
    val credentials: List<M14PublicCredentialRow> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null
)

fun M14PetPassportRow.toDomain(): M14PetPassport = M14PetPassport(
    id = id,
    petId = petId,
    passportNumber = passportNumber,
    publicCode = publicCode,
    status = enumValue(status, M14PassportStatus.DRAFT),
    displayName = displayName,
    species = enumValue(species, PetSpecies.DOG),
    breedText = breedText,
    sex = sex?.let { enumValue(it, PetSex.entries.first()) },
    birthDateEpochMs = parseM14Date(birthDate),
    primaryColor = primaryColor,
    distinctiveMarks = distinctiveMarks,
    // The RPC intentionally returns only this masked value for remote reads.
    microchipNumber = microchipMasked,
    visibility = enumValue(visibility, M14Visibility.PRIVATE),
    createdBy = createdBy.orEmpty(),
    createdAt = parseM14Timestamp(createdAt),
    updatedAt = parseM14Timestamp(updatedAt)
)

fun M14CredentialRow.toDomain(): M14Credential = M14Credential(
    id = id,
    passportId = passportId,
    type = enumValue(type, M14CredentialType.OTHER),
    title = title,
    issuerOrganizationId = issuerOrganizationId,
    issuerProfessionalId = issuerProfessionalId,
    issuedAt = issuedAt?.let(::parseM14Timestamp),
    expiresAt = expiresAt?.let(::parseM14Timestamp),
    status = enumValue(status, M14CredentialStatus.DRAFT),
    visibility = enumValue(visibility, M14Visibility.PRIVATE),
    mediaRefs = mediaRefs,
    externalReferenceMasked = externalReferenceMasked,
    notePrivate = notePrivate,
    createdBy = createdBy.orEmpty(),
    createdAt = parseM14Timestamp(createdAt),
    updatedAt = parseM14Timestamp(updatedAt)
)

fun M14VerificationRequestRow.toDomain(): M14VerificationRequest = M14VerificationRequest(
    id = id,
    credentialId = credentialId,
    requestedBy = requestedBy.orEmpty(),
    targetOrganizationId = targetOrganizationId,
    status = enumValue(status, M14VerificationRequestStatus.PENDING),
    requestedAt = parseM14Timestamp(requestedAt),
    resolvedAt = resolvedAt?.let(::parseM14Timestamp),
    resolutionReason = resolutionReason
)

fun M14PublicPassportProjectionRow.toDomain(publicCode: String = this.publicCode.orEmpty()): M14PublicPassportProjection =
    M14PublicPassportProjection(
        publicCode = publicCode,
        displayName = displayName,
        species = enumValue(species, PetSpecies.DOG),
        breedText = breedText,
        sex = sex?.let { enumValue(it, PetSex.entries.first()) },
        primaryColor = primaryColor,
        distinctiveMarks = distinctiveMarks,
        passportStatus = enumValue(status, M14PassportStatus.ACTIVE),
        microchipMasked = microchipMasked,
        credentialsPublic = credentials.map {
            M14PublicCredentialSummary(
                type = enumValue(it.type, M14CredentialType.OTHER),
                title = it.title,
                statusLabel = it.status,
                issuedAtApproxDayEpochMs = it.issuedAt?.let(::parseM14Date)
            )
        },
        updatedAtApproxDayEpochMs = parseM14Date(updatedAt) ?: 0L
    )

class SupabaseM14RemoteDataSource {
    private suspend inline fun <reified T : Any> rpc(
        name: String,
        params: JsonObject = buildJsonObject { }
    ): T = supabase.postgrest.rpc(function = name, parameters = params).decodeSingle()

    private suspend inline fun <reified T : Any> rpcList(
        name: String,
        params: JsonObject = buildJsonObject { }
    ): List<T> = supabase.postgrest.rpc(function = name, parameters = params).decodeList()

    suspend fun createPetPassport(params: JsonObject): M14PetPassportRow = rpc("m14_create_pet_passport", params)
    suspend fun getPetPassport(id: String): M14PetPassportRow = rpc("m14_get_pet_passport", idParam("p_passport_id", id))
    suspend fun getPetPassportByPet(petId: String): M14PetPassportRow = rpc("m14_get_pet_passport_by_pet", idParam("p_pet_id", petId))
    suspend fun listMyPetPassports(): List<M14PetPassportRow> = rpcList("m14_list_my_pet_passports")
    suspend fun updateMyPetPassport(params: JsonObject): M14PetPassportRow = rpc("m14_update_my_pet_passport", params)
    suspend fun activateMyPetPassport(id: String): M14PetPassportRow = rpc("m14_activate_my_pet_passport", idParam("p_passport_id", id))
    suspend fun archiveMyPetPassport(id: String, reason: String?): M14PetPassportRow = rpc(
        "m14_archive_my_pet_passport", buildJsonObject { put("p_passport_id", id); putNullable("p_reason", reason) }
    )
    suspend fun getPublicPetPassport(code: String): M14PublicPassportProjectionRow =
        rpc("m14_get_public_pet_passport", idParam("p_public_code", code))

    suspend fun createPassportCredential(params: JsonObject): M14CredentialRow =
        rpc("m14_create_passport_credential", params)
    suspend fun updateMyPassportCredential(params: JsonObject): M14CredentialRow =
        rpc("m14_update_my_passport_credential", params)
    suspend fun withdrawMyPassportCredential(id: String): M14CredentialRow =
        rpc("m14_withdraw_my_passport_credential", idParam("p_credential_id", id))
    suspend fun getPassportCredential(id: String): M14CredentialRow =
        rpc("m14_get_passport_credential", idParam("p_credential_id", id))
    suspend fun listPassportCredentials(passportId: String): List<M14CredentialRow> =
        rpcList("m14_list_passport_credentials", idParam("p_passport_id", passportId))

    suspend fun createVerificationRequest(credentialId: String, targetOrganizationId: String?): M14VerificationRequestRow =
        rpc("m14_create_verification_request", buildJsonObject {
            put("p_credential_id", credentialId)
            putNullable("p_target_organization_id", targetOrganizationId)
        })
    suspend fun cancelMyVerificationRequest(id: String): M14VerificationRequestRow =
        rpc("m14_cancel_my_verification_request", idParam("p_request_id", id))
    suspend fun getVerificationRequest(id: String): M14VerificationRequestRow =
        rpc("m14_get_verification_request", idParam("p_request_id", id))
    suspend fun listMyVerificationRequests(): List<M14VerificationRequestRow> =
        rpcList("m14_list_my_verification_requests")
    suspend fun listManagedVerificationRequests(): List<M14VerificationRequestRow> =
        rpcList("m14_list_managed_verification_requests")
}

fun createM14PassportParams(input: com.comunidapp.app.data.model.CreateM14PassportInput): JsonObject =
    buildJsonObject {
        put("p_pet_id", input.petId)
        put("p_display_name", input.displayName.trim())
        put("p_species", input.species.name)
        putNullable("p_breed_text", input.breedText)
        putNullable("p_sex", input.sex?.name)
        putNullable("p_birth_date", input.birthDateEpochMs?.let(::m14IsoDate))
        putNullable("p_primary_color", input.primaryColor)
        putNullable("p_distinctive_marks", input.distinctiveMarks)
        putNullable("p_microchip_raw", input.microchipNumber)
        put("p_visibility", input.visibility.name)
    }

fun updateM14PassportParams(id: String, input: com.comunidapp.app.data.model.UpdateM14PassportInput): JsonObject =
    buildJsonObject {
        put("p_passport_id", id)
        putNullable("p_display_name", input.displayName)
        putNullable("p_breed_text", input.breedText)
        putNullable("p_sex", input.sex?.name)
        putNullable("p_birth_date", input.birthDateEpochMs?.let(::m14IsoDate))
        putNullable("p_primary_color", input.primaryColor)
        putNullable("p_distinctive_marks", input.distinctiveMarks)
        putNullable("p_microchip_raw", input.microchipNumber)
        putNullable("p_visibility", input.visibility?.name)
    }

fun createM14CredentialParams(input: com.comunidapp.app.data.model.CreateM14CredentialInput): JsonObject =
    buildJsonObject {
        put("p_passport_id", input.passportId)
        put("p_type", input.type.name)
        put("p_title", input.title.trim())
        putNullable("p_issued_at", input.issuedAt?.let(::m14IsoTimestamp))
        putNullable("p_expires_at", input.expiresAt?.let(::m14IsoTimestamp))
        put("p_visibility", input.visibility.name)
        put("p_media_refs", JsonArray(input.mediaRefs.map(::JsonPrimitive)))
        putNullable("p_external_reference_masked", input.externalReferenceMasked)
        putNullable("p_note_private", input.notePrivate)
    }

private fun idParam(key: String, value: String): JsonObject = buildJsonObject { put(key, value) }
private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}
private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
private fun parseM14Timestamp(raw: String?): Long =
    raw?.let { value ->
        runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
            value.toLongOrNull() ?: 0L
        }
    } ?: 0L
private fun parseM14Date(raw: String?): Long? =
    raw?.let { value ->
        runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrElse { runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
    }
private fun m14IsoDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
private fun m14IsoTimestamp(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()
