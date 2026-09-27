package com.comunidapp.app.domain.authorization

enum class SessionKind {
    PERSON_SESSION,
    ADMIN_SESSION
}

data class AdminSessionInfo(
    val userId: String,
    val mustChangePassword: Boolean,
    val isRoot: Boolean,
    val mfaRequired: Boolean = false,
    val aal: String = if (mfaRequired) "aal1" else "aal2"
)

data class AdminAuthState(
    val userId: String,
    val isAdminIdentity: Boolean,
    val mustChangePassword: Boolean,
    val isRoot: Boolean,
    val mfaRequired: Boolean,
    val aal: String
) {
    fun toSessionInfo(): AdminSessionInfo = AdminSessionInfo(
        userId = userId,
        mustChangePassword = mustChangePassword,
        isRoot = isRoot,
        mfaRequired = mfaRequired,
        aal = aal
    )
}

object AdminSessionRouting {
    fun canEnterHub(context: AuthorizationContext, session: AdminSessionInfo?): Boolean {
        if (session == null) return false
        if (context.userId.isBlank() || context.userId != session.userId) return false
        return AdminAccessPolicy.canEnterAdministration(context)
    }
}

object AdminRootProtection {
    fun canMutateTarget(targetIsRoot: Boolean, actorIsRoot: Boolean): Boolean {
        if (!targetIsRoot) return true
        return actorIsRoot
    }

    fun canRevokeAnyRoleFromRoot(): Boolean = false
}
