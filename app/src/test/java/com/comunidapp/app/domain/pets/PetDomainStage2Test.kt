package com.comunidapp.app.domain.pets

import com.comunidapp.app.domain.authorization.AuthorizationContext
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.authorization.RolePermissionMatrix
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.authorization.PetAuthorizationBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetDomainStage2Test {

    private val now = 1_700_000_000_000L
    private val petId = PetId("pet-1")
    private val owner = "user-owner"
    private val other = "user-other"

    private fun personPrincipal(userId: String = owner) = PetPrincipalHolder.Person(userId)

    private fun orgPrincipal() = PetPrincipalHolder.Organization(OrganizationId("org-1"))

    private fun pet(
        principal: PetPrincipalHolder = personPrincipal(),
        status: PetLifecycleStatus = PetLifecycleStatus.ACTIVE,
        legacyOwner: String? = if (principal is PetPrincipalHolder.Person) principal.userId else null
    ) = PetAggregate(
        id = petId,
        displayName = "Luna",
        status = status,
        principal = principal,
        legacyOwnerUserId = legacyOwner,
        createdAtEpochMs = now,
        updatedAtEpochMs = now,
        deceasedAtEpochMs = if (status == PetLifecycleStatus.DECEASED) now else null,
        archivedAtEpochMs = if (status == PetLifecycleStatus.ARCHIVED) now else null
    )

    private fun principalLink(
        holder: PetPrincipalHolder = personPrincipal(),
        status: PetLinkStatus = PetLinkStatus.ACTIVE
    ) = PetResponsibility(
        id = PetResponsibilityId("resp-principal"),
        petId = petId,
        role = PetResponsibilityRole.PRINCIPAL,
        status = status,
        holder = holder,
        validFromEpochMs = now - 1,
        grantedByUserId = owner,
        createdAtEpochMs = now
    )

    private fun graph(
        aggregate: PetAggregate = pet(),
        responsibilities: List<PetResponsibility> = listOf(principalLink(aggregate.principal)),
        authorizations: List<PetAuthorization> = emptyList(),
        pending: PetTransfer? = null
    ) = PetResponsibilityGraph(aggregate, responsibilities, authorizations, pending)

    @Test
    fun aggregate_valid_with_person_principal() {
        val p = pet(personPrincipal())
        val r = principalLink(personPrincipal())
        assertTrue(PetAggregateRules.validateNew(p, r).isSuccess)
        assertTrue(PetResponsibilityRules.validateGraphInvariants(graph(p, listOf(r)), now).isSuccess)
    }

    @Test
    fun aggregate_valid_with_organization_principal() {
        val p = pet(orgPrincipal(), legacyOwner = null)
        val r = principalLink(orgPrincipal())
        assertTrue(PetAggregateRules.validateNew(p, r).isSuccess)
        assertTrue(PetResponsibilityRules.resolvePrincipal(graph(p, listOf(r))).isSuccess)
    }

    @Test
    fun reject_without_principal() {
        val g = graph(responsibilities = emptyList())
        assertEquals("PET_PRINCIPAL_MISSING", g.failureCode())
    }

    @Test
    fun reject_two_principals() {
        val second = principalLink().copy(id = PetResponsibilityId("resp-2"))
        val g = graph(responsibilities = listOf(principalLink(), second))
        assertEquals("PET_PRINCIPAL_DUPLICATE", (PetResponsibilityRules.resolvePrincipal(g).exceptionOrNull() as IllegalArgumentException).message)
    }

    @Test
    fun co_responsible_valid() {
        val co = PetResponsibility(
            id = PetResponsibilityId("co-1"),
            petId = petId,
            role = PetResponsibilityRole.CO_RESPONSIBLE,
            status = PetLinkStatus.ACTIVE,
            holder = PetPrincipalHolder.Person(other),
            validFromEpochMs = now,
            grantedByUserId = owner,
            createdAtEpochMs = now
        )
        assertTrue(PetResponsibilityRules.canAssign(graph(), co, now).isSuccess)
    }

    @Test
    fun duplicate_responsibility_rejected() {
        val co = PetResponsibility(
            id = PetResponsibilityId("co-1"),
            petId = petId,
            role = PetResponsibilityRole.CO_RESPONSIBLE,
            status = PetLinkStatus.ACTIVE,
            holder = PetPrincipalHolder.Person(other),
            validFromEpochMs = now,
            grantedByUserId = owner,
            createdAtEpochMs = now
        )
        val g = graph(responsibilities = listOf(principalLink(), co))
        val dup = co.copy(id = PetResponsibilityId("co-2"))
        assertEquals("PET_RESPONSIBILITY_DUPLICATE_ACTIVE", (PetResponsibilityRules.canAssign(g, dup, now).exceptionOrNull() as IllegalArgumentException).message)
    }

    @Test
    fun principal_as_co_responsible_rejected() {
        val bad = PetResponsibility(
            id = PetResponsibilityId("co-bad"),
            petId = petId,
            role = PetResponsibilityRole.CO_RESPONSIBLE,
            status = PetLinkStatus.ACTIVE,
            holder = personPrincipal(),
            validFromEpochMs = now,
            grantedByUserId = owner,
            createdAtEpochMs = now
        )
        assertEquals(
            "PET_PRINCIPAL_CANNOT_BE_CO_RESPONSIBLE",
            (PetResponsibilityRules.canAssign(graph(), bad, now).exceptionOrNull() as IllegalArgumentException).message
        )
    }

    @Test
    fun authorization_active() {
        val auth = sampleAuth(setOf(PetCapability.READ, PetCapability.MANAGE_HEALTH))
        assertTrue(PetAuthorizationRules.validateCreate(graph(), auth, now).isSuccess)
        assertTrue(PetAuthorizationRules.isEffectivelyActive(auth, now))
    }

    @Test
    fun authorization_expired() {
        val auth = sampleAuth(setOf(PetCapability.READ)).copy(
            validFromEpochMs = now - 10_000,
            validToEpochMs = now - 1
        )
        assertFalse(PetAuthorizationRules.isEffectivelyActive(auth, now))
    }

    @Test
    fun authorization_revoked() {
        val auth = sampleAuth(setOf(PetCapability.READ)).copy(status = PetLinkStatus.REVOKED)
        assertFalse(PetAuthorizationRules.isEffectivelyActive(auth, now))
    }

    @Test(expected = IllegalArgumentException::class)
    fun authorization_cannot_grant_transfer() {
        PetAuthorization(
            id = PetAuthorizationId("a1"),
            petId = petId,
            granteeUserId = other,
            capabilities = setOf(PetCapability.INITIATE_TRANSFER),
            status = PetLinkStatus.ACTIVE,
            validFromEpochMs = now,
            grantedByUserId = owner,
            createdAtEpochMs = now
        )
    }

    @Test
    fun explicit_capabilities_only_for_authorized() {
        val auth = sampleAuth(setOf(PetCapability.READ))
        val g = graph(authorizations = listOf(auth))
        val caps = PetEffectiveCapabilities.forActor(g, other, now)
        assertEquals(setOf(PetCapability.READ), caps)
        assertFalse(PetCapability.ARCHIVE in caps)
        assertFalse(PetCapability.INITIATE_TRANSFER in caps)
    }

    @Test
    fun principal_default_capabilities() {
        val caps = PetEffectiveCapabilities.forActor(graph(), owner, now)
        assertTrue(PetCapability.INITIATE_TRANSFER in caps)
        assertTrue(PetCapability.MARK_DECEASED in caps)
        assertTrue(PetCapability.MANAGE_RESPONSIBILITIES in caps)
        assertFalse(PetCapability.ACCEPT_TRANSFER in caps) // destino, no rol principal
    }

    @Test
    fun transfer_pending_valid() {
        val t = pendingTransfer()
        assertTrue(PetTransferRules.validateRequest(graph(), t, now).isSuccess)
    }

    @Test
    fun transfer_accept_valid() {
        val t = pendingTransfer()
        assertTrue(PetTransferRules.validateTransition(t, PetTransferStatus.ACCEPTED, now).isSuccess)
        assertTrue(PetTransferRules.canActorAccept(t, other))
        val updated = PetTransferRules.applyAcceptedPrincipal(pet(), t).getOrThrow()
        assertEquals(PetPrincipalHolder.Person(other), updated.principal)
        assertEquals(other, updated.legacyOwnerUserId)
    }

    @Test
    fun transfer_reject_cancel_expire_valid() {
        val t = pendingTransfer()
        assertTrue(PetTransferRules.validateTransition(t, PetTransferStatus.REJECTED, now).isSuccess)
        assertTrue(PetTransferRules.validateTransition(t, PetTransferStatus.CANCELLED, now).isSuccess)
        assertTrue(
            PetTransferRules.validateTransition(
                t.copy(expiresAtEpochMs = now - 1),
                PetTransferStatus.EXPIRED,
                now
            ).isSuccess
        )
    }

    @Test
    fun invalid_transition_rejected() {
        val accepted = pendingTransfer().copy(status = PetTransferStatus.ACCEPTED)
        assertEquals(
            "PET_TRANSFER_INVALID_TRANSITION",
            (PetTransferRules.validateTransition(accepted, PetTransferStatus.PENDING, now)
                .exceptionOrNull() as IllegalArgumentException).message
        )
        assertFalse(PetTransferRules.canTransition(PetTransferStatus.REJECTED, PetTransferStatus.ACCEPTED))
        assertFalse(PetTransferRules.canTransition(PetTransferStatus.CANCELLED, PetTransferStatus.ACCEPTED))
        assertFalse(PetTransferRules.canTransition(PetTransferStatus.EXPIRED, PetTransferStatus.ACCEPTED))
    }

    @Test
    fun transfer_same_principal_rejected() {
        val t = pendingTransfer().copy(toPrincipal = personPrincipal(owner))
        assertEquals(
            "PET_TRANSFER_SAME_PRINCIPAL",
            (PetTransferRules.validateRequest(graph(), t, now).exceptionOrNull() as IllegalArgumentException).message
        )
    }

    @Test
    fun transfer_deceased_rejected() {
        val deceased = pet(status = PetLifecycleStatus.DECEASED)
        assertEquals(
            "PET_TRANSFER_DECEASED",
            (PetTransferRules.validateRequest(graph(deceased), pendingTransfer(), now)
                .exceptionOrNull() as IllegalArgumentException).message
        )
    }

    @Test
    fun transfer_archived_rejected() {
        val archived = pet(status = PetLifecycleStatus.ARCHIVED)
        assertEquals(
            "PET_TRANSFER_ARCHIVED",
            (PetTransferRules.validateRequest(graph(archived), pendingTransfer(), now)
                .exceptionOrNull() as IllegalArgumentException).message
        )
    }

    @Test
    fun microchip_normalized_and_empty() {
        assertEquals("ABC123", MicrochipNormalizer.normalizeOrNull(" abc-123 "))
        assertNull(MicrochipNormalizer.normalizeOrNull("   "))
        assertTrue(MicrochipNormalizer.isAbsent(null))
        assertTrue(MicrochipNormalizer.isAbsent(""))
    }

    @Test
    fun m05_avatar_ref() {
        val avatar = PetAvatarRef(fileAssetId = "asset-1", legacyPhotoUrl = "https://legacy.example/x.jpg")
        val media = PetMediaBundle(avatar = avatar, gallery = listOf(PetGalleryItemRef("asset-2", 1)))
        assertEquals("asset-1", media.avatar!!.fileAssetId)
        assertEquals(1, media.gallery.single().sortOrder)
    }

    @Test
    fun history_preserved_conceptually_on_status_change() {
        val entry = PetStatusHistoryEntry(
            petId = petId,
            fromStatus = PetLifecycleStatus.ACTIVE,
            toStatus = PetLifecycleStatus.DECEASED,
            reasonCode = "OWNER_REPORT",
            actorUserId = owner,
            createdAtEpochMs = now,
            correlationId = "corr-1"
        )
        assertEquals(PetLifecycleStatus.ACTIVE, entry.fromStatus)
        assertEquals(PetLifecycleStatus.DECEASED, entry.toStatus)
        assertTrue(PetAggregateRules.canMarkDeceased(PetLifecycleStatus.ACTIVE).isSuccess)
    }

    @Test
    fun staff_bridge_uses_m02_permission_codes() {
        val perms = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.ADMIN))
        assertTrue(PermissionCode.PET_READ in perms)
        assertTrue(PermissionCode.PET_VIEW_HISTORY in perms)
        val ctx = AuthorizationContext("admin-1", setOf(PlatformRoleCode.ADMIN), perms)
        val staff = PetAuthorizationBridge.staffCapabilities(ctx)
        assertTrue(PetCapability.READ in staff)
        assertTrue(
            PetAuthorizationBridge.decide(
                graph = graph(),
                actorUserId = "stranger",
                required = PetCapability.READ,
                nowEpochMs = now,
                platform = ctx
            )
        )
    }

    @Test
    fun capability_catalog_matches_permission_codes() {
        PetCapability.entries.forEach { cap ->
            assertEquals(cap, PetCapability.fromCode(cap.code))
            assertTrue(PermissionCode.fromCode(cap.code) != null)
        }
    }

    private fun sampleAuth(caps: Set<PetCapability>) = PetAuthorization(
        id = PetAuthorizationId("auth-1"),
        petId = petId,
        granteeUserId = other,
        capabilities = caps,
        status = PetLinkStatus.ACTIVE,
        validFromEpochMs = now - 1,
        grantedByUserId = owner,
        createdAtEpochMs = now
    )

    private fun pendingTransfer() = PetTransfer(
        id = PetTransferId("tr-1"),
        petId = petId,
        fromPrincipal = personPrincipal(owner),
        toPrincipal = personPrincipal(other),
        status = PetTransferStatus.PENDING,
        requestedAtEpochMs = now,
        expiresAtEpochMs = now + 86_400_000,
        requestedByUserId = owner
    )

    private fun PetResponsibilityGraph.failureCode(): String =
        (PetResponsibilityRules.validateGraphInvariants(this, now).exceptionOrNull() as IllegalArgumentException).message!!
}
