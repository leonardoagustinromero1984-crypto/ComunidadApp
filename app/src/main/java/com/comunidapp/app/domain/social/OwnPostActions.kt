package com.comunidapp.app.domain.social

/**
 * Actions on a publication.
 *
 * Guardar stays available on the author's own post: Mis guardados is a personal
 * bookmark and the physical QA confirmed that saving one's own alert is valid.
 * Reportar and Bloquear autor are hidden when the viewer is the author.
 * The same check belongs in the view model so a stale callback cannot report self.
 */
object OwnPostActions {
    fun showSave(): Boolean = true

    fun showReport(authorId: String, currentUserId: String?): Boolean {
        val viewer = currentUserId?.trim().orEmpty()
        if (authorId.isBlank() || viewer.isBlank()) return false
        return authorId != viewer
    }

    fun showBlock(authorId: String, currentUserId: String?): Boolean =
        showReport(authorId, currentUserId)
}
