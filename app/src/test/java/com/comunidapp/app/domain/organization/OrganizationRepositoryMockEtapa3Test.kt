package com.comunidapp.app.domain.organization

import com.comunidapp.app.data.remote.storage.OrganizationMediaStorageService
import com.comunidapp.app.data.remote.storage.StoragePaths
import com.comunidapp.app.data.repository.MockOrganizationMembershipRepository
import com.comunidapp.app.data.repository.MockOrganizationPermissionRepository
import com.comunidapp.app.data.repository.MockOrganizationRepository
import com.comunidapp.app.data.repository.OrganizationRowMapper
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrganizationMediaPathTest {

    private val media = OrganizationMediaStorageService()

    @Test
    fun storage_paths_match_expected_shape() {
        val orgId = "11111111-1111-1111-1111-111111111111"
        assertEquals(
            "organizations/$orgId/logo/logo.jpg",
            StoragePaths.organizationLogo(orgId)
        )
        assertEquals(
            "organizations/$orgId/cover/cover.jpg",
            StoragePaths.organizationCover(orgId)
        )
    }

    @Test
    fun validates_path_for_organization() {
        val orgId = "11111111-1111-1111-1111-111111111111"
        val path = StoragePaths.organizationLogo(orgId)
        assertTrue(media.isValidPath(path, orgId))
        assertFalse(media.isValidPath(path, "22222222-2222-2222-2222-222222222222"))
        assertFalse(media.isValidPath("organizations/$orgId/logo/../secret.jpg", orgId))
        assertFalse(media.isValidPath("users/$orgId/avatar/x.jpg", orgId))
    }
}

class OrganizationRepositoryMockEtapa3Test {

    private lateinit var memberships: MockOrganizationMembershipRepository
    private lateinit var orgs: MockOrganizationRepository

    @Before
    fun setUp() {
        memberships = MockOrganizationMembershipRepository()
        orgs = MockOrganizationRepository(memberships)
        orgs.resetForTests()
        memberships.resetForTests()
        orgs.actingUserId = "user-owner"
    }

    @Test
    fun create_stores_owner_membership_and_lists_mine() = runBlocking {
        val draft = OrganizationValidators.validateCreate(
            legalName = "",
            publicName = "Patitas",
            type = OrganizationType.SHELTER,
            typeDescription = null,
            slugRaw = "patitas-caba",
            city = "CABA",
            province = "BA",
            countryCode = "AR"
        ).getOrThrow()

        val created = orgs.createOrganization(draft).getOrThrow()
        assertEquals("Patitas", created.legalName)
        assertEquals(OrganizationStatus.DRAFT, created.status)
        assertEquals(OrganizationVerificationStatus.NOT_REQUESTED, created.verificationStatus)

        val mine = orgs.getMyOrganizations()
        assertEquals(1, mine.size)
        assertEquals(created.id, mine.first().id)

        val membership = memberships.getActiveMembership(created.id, "user-owner")
        assertNotNull(membership)
        assertEquals(OrganizationRoleCode.OWNER, membership!!.role)
    }

    @Test
    fun public_projection_hides_pii_until_opt_in() = runBlocking {
        val draft = OrganizationValidators.validateCreate(
            legalName = "Legal",
            publicName = "PublicOrg",
            type = OrganizationType.NGO,
            typeDescription = null,
            slugRaw = "public-org-1"
        ).getOrThrow()
        val created = orgs.createDraft(draft, "user-owner").getOrThrow()
        orgs.updateMyOrganization(
            UpdateOrganizationCommand(
                organizationId = created.id,
                contactEmail = "secret@org.test",
                contactPhone = "111",
                contactEmailPublic = false,
                contactPhonePublic = false
            )
        )
        // DRAFT no es público
        assertNull(orgs.getPublicBySlug("public-org-1").getOrThrow())

        orgs.seedForTests(
            created.copy(
                status = OrganizationStatus.ACTIVE,
                institutionalEmail = "secret@org.test",
                institutionalPhone = "111",
                contactVisibility = OrganizationContactVisibility(showEmail = false, showPhone = false)
            )
        )
        val public = orgs.getPublicBySlug("public-org-1").getOrThrow()
        assertNotNull(public)
        assertNull(public!!.publicEmail)
        assertNull(public.publicPhone)
        assertEquals("PublicOrg", public.publicName)
    }

    @Test
    fun request_verification_only_to_pending() = runBlocking {
        val draft = OrganizationValidators.validateCreate(
            legalName = "Legal",
            publicName = "VerifyMe",
            type = OrganizationType.NGO,
            typeDescription = null,
            slugRaw = "verify-me-org"
        ).getOrThrow()
        val created = orgs.createDraft(draft, "user-owner").getOrThrow()
        val pending = orgs.requestVerification(created.id).getOrThrow()
        assertEquals(OrganizationVerificationStatus.PENDING, pending.verificationStatus)
        assertTrue(orgs.requestVerification(created.id).isFailure)

        orgs.seedForTests(pending.copy(verificationStatus = OrganizationVerificationStatus.REJECTED))
        val again = orgs.requestVerification(created.id).getOrThrow()
        assertEquals(OrganizationVerificationStatus.PENDING, again.verificationStatus)
        assertTrue(again.verificationStatus != OrganizationVerificationStatus.VERIFIED)
    }

    @Test
    fun permission_repo_denies_on_missing_membership() = runBlocking {
        val draft = OrganizationValidators.validateCreate(
            legalName = "Legal",
            publicName = "DenyOrg",
            type = OrganizationType.NGO,
            typeDescription = null,
            slugRaw = "deny-org-1"
        ).getOrThrow()
        val created = orgs.createDraft(draft, "user-owner").getOrThrow()
        val perms = MockOrganizationPermissionRepository(orgs, memberships)
        assertFalse(
            perms.hasPermission(
                created.id,
                "stranger",
                AccountStatus.ACTIVE,
                OrganizationPermissionCode.ORGANIZATION_UPDATE
            )
        )
        assertTrue(
            perms.hasPermission(
                created.id,
                "user-owner",
                AccountStatus.ACTIVE,
                OrganizationPermissionCode.ORGANIZATION_UPDATE
            )
        )
    }
}

class OrganizationRowMapperDenyPatternTest {

    @Test
    fun mapper_parses_known_enums_and_falls_back_safely() {
        assertEquals(OrganizationType.SHELTER, OrganizationRowMapper.parseType("shelter"))
        assertEquals(OrganizationType.OTHER, OrganizationRowMapper.parseType("UNKNOWN_TYPE"))
        assertEquals(OrganizationStatus.ACTIVE, OrganizationRowMapper.parseStatus("ACTIVE"))
        assertEquals(OrganizationStatus.DRAFT, OrganizationRowMapper.parseStatus("???") )
        assertEquals(
            OrganizationVerificationStatus.PENDING,
            OrganizationRowMapper.parseVerification("PENDING")
        )
        assertNull(OrganizationRowMapper.run { "not-iso".toEpochMillis() })
        assertNotNull(OrganizationRowMapper.run { "2024-01-15T12:00:00Z".toEpochMillis() })
    }

    @Test
    fun permission_code_from_code_is_allowlist() {
        assertEquals(
            OrganizationPermissionCode.ORGANIZATION_UPDATE,
            OrganizationPermissionCode.fromCode("organization.update")
        )
        assertNull(OrganizationPermissionCode.fromCode("organization.hack"))
    }
}
