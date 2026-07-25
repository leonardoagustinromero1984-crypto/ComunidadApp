package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14VerificationRequestStatus
import com.comunidapp.app.data.remote.supabase.m14.M14CredentialRow
import com.comunidapp.app.data.remote.supabase.m14.M14PetPassportRow
import com.comunidapp.app.data.remote.supabase.m14.M14PublicCredentialRow
import com.comunidapp.app.data.remote.supabase.m14.M14PublicPassportProjectionRow
import com.comunidapp.app.data.remote.supabase.m14.M14VerificationRequestRow
import com.comunidapp.app.data.remote.supabase.m14.toDomain
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M14SupabaseMappingTest {
    @Test
    fun passport_row_maps_server_mask_and_date_without_network() {
        val row = M14PetPassportRow(
            id = "passport-1",
            petId = "pet-1",
            passportNumber = "LV-AR-2026-ABCD",
            publicCode = "PUB-ABC",
            status = "ACTIVE",
            visibility = "PUBLIC_REDACTED",
            displayName = "Luna",
            species = "DOG",
            birthDate = "2021-05-04",
            microchipMasked = "********1234",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-02T00:00:00Z"
        )

        val passport = row.toDomain()

        assertEquals(M14PassportStatus.ACTIVE, passport.status)
        assertEquals("********1234", passport.microchipNumber)
        assertEquals(
            LocalDate.of(2021, 5, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            passport.birthDateEpochMs
        )
    }

    @Test
    fun credential_request_and_public_projection_map_optional_fields() {
        val credential = M14CredentialRow(
            id = "credential-1",
            passportId = "passport-1",
            type = "VACCINATION_ATTESTATION",
            title = "Antirrábica",
            status = "VERIFIED",
            visibility = "PUBLIC_REDACTED"
        ).toDomain()
        val request = M14VerificationRequestRow(
            id = "request-1",
            credentialId = "credential-1",
            status = "PENDING",
            requestedAt = "2026-02-03T10:00:00Z"
        ).toDomain()
        val projection = M14PublicPassportProjectionRow(
            displayName = "Luna",
            species = "DOG",
            status = "ACTIVE",
            credentials = listOf(
                M14PublicCredentialRow(
                    type = "VACCINATION_ATTESTATION",
                    title = "Antirrábica",
                    status = "VERIFIED",
                    issuedAt = "2026-01-15T12:00:00Z"
                )
            ),
            updatedAt = "2026-02-03"
        ).toDomain("PUB-ABC")

        assertEquals(M14CredentialType.VACCINATION_ATTESTATION, credential.type)
        assertEquals(M14CredentialStatus.VERIFIED, credential.status)
        assertEquals(M14VerificationRequestStatus.PENDING, request.status)
        assertEquals("", request.requestedBy)
        assertNull(request.targetOrganizationId)
        assertEquals("PUB-ABC", projection.publicCode)
        assertEquals(M14CredentialType.VACCINATION_ATTESTATION, projection.credentialsPublic.single().type)
    }
}
