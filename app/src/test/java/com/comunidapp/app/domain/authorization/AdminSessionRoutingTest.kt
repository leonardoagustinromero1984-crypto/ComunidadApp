package com.comunidapp.app.domain.authorization

import com.comunidapp.app.domain.auth.LoginIdentifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminSessionRoutingTest {

    private fun ctx(userId: String, vararg roles: PlatformRoleCode): AuthorizationContext {
        val set = roles.toSet()
        return AuthorizationContext(
            userId = userId,
            roles = set,
            permissions = RolePermissionMatrix.permissionsFor(set)
        )
    }

    @Test
    fun identifierClassificationDoesNotNameAdminAccounts() {
        assertTrue(LoginIdentifier.looksLikeEmail("ana@email.com"))
        assertFalse(LoginIdentifier.looksLikeEmail("qa.admin"))
        assertFalse(LoginIdentifier.looksLikeEmail("  nobody  "))
    }

    @Test
    fun missingIdentityCannotEnterHub() {
        val ctx = ctx("admin-1", PlatformRoleCode.SUPERADMIN)
        assertFalse(AdminSessionRouting.canEnterHub(ctx, session = null))
    }

    @Test
    fun identityWithoutPlatformPermissionIsDenied() {
        val session = AdminSessionInfo("admin-1", mustChangePassword = false, isRoot = true)
        val ctx = ctx("admin-1", PlatformRoleCode.USER)
        assertFalse(AdminSessionRouting.canEnterHub(ctx, session))
    }

    @Test
    fun superadminIdentityEntersHub() {
        val session = AdminSessionInfo("admin-1", mustChangePassword = false, isRoot = true)
        val ctx = ctx("admin-1", PlatformRoleCode.SUPERADMIN)
        assertTrue(AdminSessionRouting.canEnterHub(ctx, session))
    }

    @Test
    fun userIdMismatchIsDenied() {
        val session = AdminSessionInfo("admin-1", mustChangePassword = false, isRoot = true)
        val ctx = ctx("other", PlatformRoleCode.SUPERADMIN)
        assertFalse(AdminSessionRouting.canEnterHub(ctx, session))
    }

    @Test
    fun rootCannotBeMutatedByNonRoot() {
        assertFalse(AdminRootProtection.canMutateTarget(targetIsRoot = true, actorIsRoot = false))
        assertTrue(AdminRootProtection.canMutateTarget(targetIsRoot = true, actorIsRoot = true))
        assertTrue(AdminRootProtection.canMutateTarget(targetIsRoot = false, actorIsRoot = false))
        assertFalse(AdminRootProtection.canRevokeAnyRoleFromRoot())
    }
}
