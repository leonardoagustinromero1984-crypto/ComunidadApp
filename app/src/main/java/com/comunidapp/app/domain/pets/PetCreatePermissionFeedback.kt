package com.comunidapp.app.domain.pets

/**
 * A permission snackbar must not be shown when pet creation already succeeded.
 * Stale `canManageMedia` (context still resolving, or a pre-create capability
 * snapshot) is not a denial of an authorized create.
 */
object PetCreatePermissionFeedback {
    fun suppressPermissionErrorAfterAuthorizedCreate(
        createSucceeded: Boolean,
        isEditMode: Boolean
    ): Boolean = createSucceeded && !isEditMode
}
