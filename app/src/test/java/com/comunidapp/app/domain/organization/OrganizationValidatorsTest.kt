package com.comunidapp.app.domain.organization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationSlugValidatorsTest {

    @Test
    fun valid_slug_normalizes_case() {
        val result = OrganizationSlugValidators.validate("My-Shelter1")
        assertTrue(result.isSuccess)
        assertEquals("my-shelter1", result.getOrNull()!!.value)
    }

    @Test
    fun rejects_too_short_and_too_long() {
        assertTrue(OrganizationSlugValidators.validate("ab").isFailure)
        assertTrue(OrganizationSlugValidators.validate("a".repeat(51)).isFailure)
    }

    @Test
    fun rejects_reserved_and_spaces() {
        assertTrue(OrganizationSlugValidators.validate("admin").isFailure)
        assertTrue(OrganizationSlugValidators.validate("org").isFailure)
        assertTrue(OrganizationSlugValidators.validate("my shelter").isFailure)
    }

    @Test
    fun rejects_consecutive_hyphens_and_edge_hyphens() {
        assertTrue(OrganizationSlugValidators.validate("my--shelter").isFailure)
        assertTrue(OrganizationSlugValidators.validate("-myshelter").isFailure)
        assertTrue(OrganizationSlugValidators.validate("myshelter-").isFailure)
    }
}

class OrganizationValidatorsTest {

    @Test
    fun valid_create_draft() {
        val result = OrganizationValidators.validateCreate(
            legalName = "Asociación Patitas",
            publicName = "Patitas",
            type = OrganizationType.SHELTER,
            typeDescription = null,
            slugRaw = "patitas-caba",
            description = "Refugio",
            countryCode = "ar"
        )
        assertTrue(result.isSuccess)
        assertEquals("patitas-caba", result.getOrThrow().slug.value)
        assertEquals("AR", result.getOrThrow().countryCode)
    }

    @Test
    fun other_type_requires_description() {
        val result = OrganizationValidators.validateCreate(
            legalName = "Algo",
            publicName = "Algo",
            type = OrganizationType.OTHER,
            typeDescription = null,
            slugRaw = "algo-org"
        )
        assertTrue(result.isFailure)
        assertEquals(
            OrganizationValidationErrorCode.TYPE_OTHER_NEEDS_DESCRIPTION.name,
            (result.exceptionOrNull() as OrganizationValidationException).error.code
        )
    }

    @Test
    fun optional_legal_name_uses_public_name() {
        val result = OrganizationValidators.validateCreate(
            legalName = "",
            publicName = "Patitas",
            type = OrganizationType.SHELTER,
            typeDescription = null,
            slugRaw = "patitas-opt",
            city = "CABA",
            province = "BA"
        )
        assertTrue(result.isSuccess)
        assertEquals("Patitas", result.getOrThrow().legalName)
        assertEquals("CABA", result.getOrThrow().city)
    }

    @Test
    fun rejects_short_name() {
        val result = OrganizationValidators.validateCreate(
            legalName = "A",
            publicName = "Patitas",
            type = OrganizationType.NGO,
            typeDescription = null,
            slugRaw = "patitas"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun public_projection_hides_contact_by_default() {
        val org = Organization(
            id = OrganizationId("o1"),
            legalName = "Legal",
            publicName = "Public",
            slug = OrganizationSlug.ofNormalized("public-org"),
            type = OrganizationType.NGO,
            institutionalEmail = "secret@org.test",
            institutionalPhone = "123",
            createdByUserId = "u1"
        )
        val public = OrganizationValidators.toPublic(org)
        assertNull(public.publicEmail)
        assertNull(public.publicPhone)
        assertEquals("Public", public.publicName)
    }

    @Test
    fun cannot_self_verify() {
        assertTrue(
            OrganizationValidators.assertNotSelfVerification("u1", "u1").isFailure
        )
        assertTrue(
            OrganizationValidators.assertNotSelfVerification("u1", "u2").isSuccess
        )
    }

    @Test
    fun foster_home_is_not_an_organization_type() {
        // D-M03-04: FOSTER_HOME no es OrganizationType; capacidad personal.
        assertTrue(
            OrganizationType.entries.none { it.name == "FOSTER_HOME" }
        )
    }
}
