package com.comunidapp.app.domain.authorization

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.LeoverModule
import com.comunidapp.app.domain.ModulePermissions
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.user.PublicUserProfile
import com.comunidapp.app.domain.user.UserProfileMapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthorizationServiceTest {

    @Test
    fun deny_by_default_empty_context() {
        val ctx = AuthorizationContext.empty("u1")
        assertFalse(AuthorizationService.hasPermission(ctx, PermissionCode.MODERATION_VIEW))
        assertTrue(AuthorizationService.decide(ctx, PermissionCode.PROFILE_READ_OWN) is AuthorizationDecision.Denied)
    }

    @Test
    fun user_role_has_profile_not_moderation() {
        val ctx = AuthorizationContext(
            userId = "u1",
            roles = setOf(PlatformRoleCode.USER),
            permissions = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.USER))
        )
        assertTrue(AuthorizationService.hasPermission(ctx, PermissionCode.PROFILE_UPDATE_OWN))
        assertFalse(AuthorizationService.canViewModeration(ctx))
    }

    @Test
    fun moderator_can_view_moderation() {
        val roles = setOf(PlatformRoleCode.USER, PlatformRoleCode.MODERATOR)
        val ctx = AuthorizationContext(
            userId = "mod1",
            roles = roles,
            permissions = RolePermissionMatrix.permissionsFor(roles)
        )
        assertTrue(AuthorizationService.canViewModeration(ctx))
        assertTrue(AuthorizationService.hasPermission(ctx, PermissionCode.MODERATION_MANAGE_REPORTS))
    }

    @Test
    fun account_type_and_modules_do_not_grant_permissions() {
        assertFalse(AuthorizationService.grantsFromAccountTypeOrModules())
        val adminUser = User(
            id = "x",
            name = "AdminFake",
            email = "a@b.com",
            accountType = AccountType.SHELTER,
            activeModules = setOf(LeoverModule.ADMIN)
        )
        assertFalse(ModulePermissions.canModerateContent(adminUser))
        assertFalse(RolePermissions.canModerateContent(adminUser))
    }

    @Test
    fun public_profile_strips_pii() {
        val user = User(
            id = "1",
            name = "Ana",
            email = "ana@secret.com",
            bio = "hola",
            locationText = "CABA",
            phone = "111",
            profilePrivate = false
        )
        val public: PublicUserProfile = UserProfileMapper.toPublicUserProfile(user)
        assertEquals("Ana", public.displayName)
        assertNull(public.username)
        // PublicUserProfile type has no email/phone fields — compile-time safety
        assertEquals("hola", public.bio)
    }
}

class MockPermissionRepositoryTest {

    private lateinit var repo: MockPermissionRepository

    @Before
    fun setUp() {
        repo = MockPermissionRepository()
        repo.resetForTests()
    }

    @Test
    fun default_user_denied_moderation() = runTest {
        assertFalse(repo.hasPermission("u1", PermissionCode.MODERATION_VIEW))
        assertTrue(repo.hasPermission("u1", PermissionCode.PROFILE_READ_OWN))
    }

    @Test
    fun moderator_role_allows_view() = runTest {
        repo.setRolesForTests(
            "mod1",
            setOf(PlatformRoleCode.USER, PlatformRoleCode.MODERATOR)
        )
        assertTrue(repo.hasPermission("mod1", PermissionCode.MODERATION_VIEW))
    }
}
