package com.comunidapp.app.domain.organization

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationResourceLinkRulesTest {

    @Test
    fun personal_shelter_without_organization_is_valid() {
        val link = OrganizationResourceLinkRules.personalShelter("shelter-1", "user-1")
        assertTrue(OrganizationResourceLinkRules.validateExclusiveOwnership(link).isSuccess)
        assertNull(link.organizationId)
    }

    @Test
    fun personal_and_organization_service_are_valid_separately() {
        val personal = OrganizationResourceLinkRules.personalService("svc-1", "user-1")
        val org = OrganizationResourceLinkRules.organizationService(
            resourceId = "svc-2",
            organizationId = OrganizationId("org-1"),
            legacyOwnerUserId = "user-2"
        )
        assertTrue(OrganizationResourceLinkRules.validateExclusiveOwnership(personal).isSuccess)
        assertTrue(OrganizationResourceLinkRules.validateExclusiveOwnership(org).isSuccess)
    }

    @Test
    fun dual_primary_ownership_invalid() {
        val personal = OrganizationResourceLinkRules.personalService("svc-dual", "user-1")
        val org = OrganizationResourceLinkRules.organizationService(
            resourceId = "svc-dual",
            organizationId = OrganizationId("org-1"),
            legacyOwnerUserId = "user-1"
        )
        val result = OrganizationResourceLinkRules.assertNoDualPrimaryOwnership(
            listOf(personal, org)
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun account_type_does_not_create_link() {
        assertNull(OrganizationResourceLinkRules.linkFromAccountType())
    }

    @Test
    fun personal_link_rejects_organization_id() {
        var failed = false
        try {
            OrganizationResourceLink(
                resourceType = OrganizationResourceType.SHELTER_LISTING,
                resourceId = "s1",
                ownerKind = OrganizationResourceOwnerKind.PERSONAL,
                organizationId = OrganizationId("org-x"),
                legacyOwnerUserId = "u1"
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
