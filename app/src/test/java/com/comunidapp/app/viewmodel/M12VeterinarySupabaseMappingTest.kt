package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryClinicRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryOpeningHoursRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryProfessionalRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryServiceRow
import com.comunidapp.app.data.remote.supabase.m12.toDomain
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.repository.FosterSecureRefValidator
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M12 Bloque 2 — mapeo snake_case / dominio (sin Supabase real).
 */
class M12VeterinarySupabaseMappingTest {

    @Test
    fun maps_clinic_snake_case() {
        val domain = VeterinaryClinicRow(
            id = "c1",
            organizationId = "o1",
            branchId = "b1",
            displayName = "Clínica",
            status = "ACTIVE",
            verificationStatus = "VERIFIED",
            publicZoneText = "Norte",
            publicContactEnabled = true,
            publicPhone = "123",
            websiteUrl = "https://example.test",
            socialLinks = mapOf("ig" to "https://instagram.com/x"),
            logoAssetRef = "m05://logo",
            coverAssetRef = "file_asset:cover",
            offersEmergencyCare = true,
            isOpen24Hours = false,
            createdAt = "2026-01-15T12:00:00Z",
            updatedAt = "2026-01-15T12:00:00Z"
        ).toDomain()
        assertEquals("o1", domain.organizationId)
        assertEquals(VeterinaryClinicStatus.ACTIVE, domain.status)
        assertEquals(VeterinaryVerificationStatus.VERIFIED, domain.verificationStatus)
        assertEquals("https://example.test", domain.websiteUrl)
        assertEquals("m05://logo", domain.logoAssetRef)
        assertEquals(1, domain.socialLinks.size)
    }

    @Test
    fun maps_professional_and_specialties() {
        val domain = VeterinaryProfessionalRow(
            id = "p1",
            displayName = "Dra",
            licenseNumber = null,
            verificationStatus = "UNVERIFIED",
            specialties = listOf("SURGERY", "NOPE"),
            publicContactEnabled = false,
            publicPhone = "hidden"
        ).toDomain()
        assertTrue(VeterinarySpecialty.SURGERY in domain.specialties)
        assertTrue(VeterinarySpecialty.UNKNOWN in domain.specialties)
    }

    @Test
    fun maps_service_species_and_hours() {
        val svc = VeterinaryServiceRow(
            id = "s1",
            clinicId = "c1",
            name = "Consulta",
            category = "CONSULTATION",
            species = listOf("DOG", "XYZ"),
            requiresAppointment = true,
            emergencyAvailable = false
        ).toDomain()
        assertEquals(VeterinaryServiceCategory.CONSULTATION, svc.category)
        assertEquals(2, svc.species.size)

        val hours = VeterinaryOpeningHoursRow(
            clinicId = "c1",
            dayOfWeek = 1,
            closed = false,
            opensAt = "09:00:00",
            closesAt = "18:00"
        ).toDomain()
        assertEquals(DayOfWeek.MONDAY, hours.dayOfWeek)
        assertEquals(LocalTime.of(9, 0), hours.opensAt)
        assertEquals(LocalTime.of(18, 0), hours.closesAt)
    }

    @Test
    fun null_contact_and_m05_vs_website() {
        val clinic = VeterinaryClinicRow(
            id = "c",
            organizationId = "o",
            displayName = "X",
            publicZoneText = "Z",
            publicContactEnabled = false,
            publicPhone = null,
            publicEmail = null,
            websiteUrl = "https://safe.test",
            logoAssetRef = "m05://a"
        ).toDomain()
        assertNull(clinic.publicPhone)
        assertTrue(clinic.websiteUrl!!.startsWith("https://"))
        assertFalse(FosterSecureRefValidator.isUnsafePublicReference("m05://a"))
        assertTrue(FosterSecureRefValidator.isUnsafePublicReference("https://cdn/x"))
    }

    @Test
    fun stable_rpc_error_mapping() {
        val ex = Exception("ERROR: VETERINARY_CLINIC_NOT_FOUND")
        assertEquals("VETERINARY_CLINIC_NOT_FOUND", M12VeterinaryErrorMapper.codeOf(ex))
        assertFalse(M12VeterinaryErrorMapper.userMessage("NETWORK").contains("postgrest"))
    }
}
