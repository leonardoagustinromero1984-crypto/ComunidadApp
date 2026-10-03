package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.domain.social.SocialPostMedia

enum class VitaCoraHistoryDestinationKind {
    LOST_FOUND_CASE,
    SOCIAL_POST
}

data class VitaCoraHistoryDestination(
    val kind: VitaCoraHistoryDestinationKind,
    val entityId: String
)

/**
 * A history row navigates only when it represents a Lost/Found publication or
 * case and a related id exists. Rows without a target stay inert.
 */
object VitaCoraHistoryNavigation {
    fun destination(
        metadataEvent: String?,
        reason: String?,
        compositionJson: String?,
        socialContentId: String?,
        lostFoundCaseId: String?
    ): VitaCoraHistoryDestination? {
        val event = metadataEvent?.trim()?.uppercase().orEmpty()
        val reasonUp = reason?.trim()?.uppercase().orEmpty()
        val alert = SocialPostMedia.alertKind(compositionJson)?.uppercase()
        val postType = SocialPostMedia.postType(compositionJson)
        val representsCase = event in CASE_EVENTS ||
            reasonUp in CASE_EVENTS ||
            alert == "LOST" ||
            alert == "FOUND" ||
            postType.equals("LOST_FOUND", ignoreCase = true) ||
            reason?.trim().equals("Foto del hallazgo", ignoreCase = true)
        if (!representsCase) return null
        val caseId = lostFoundCaseId?.trim()?.takeIf { it.isNotEmpty() }
            ?: SocialPostMedia.lostFoundCaseId(compositionJson)
        if (!caseId.isNullOrBlank()) {
            return VitaCoraHistoryDestination(VitaCoraHistoryDestinationKind.LOST_FOUND_CASE, caseId)
        }
        val postId = socialContentId?.trim()?.takeIf { it.isNotEmpty() }
        if (!postId.isNullOrBlank() &&
            (alert == "LOST" || alert == "FOUND" || postType.equals("LOST_FOUND", ignoreCase = true))
        ) {
            return VitaCoraHistoryDestination(VitaCoraHistoryDestinationKind.SOCIAL_POST, postId)
        }
        return null
    }

    private val CASE_EVENTS = setOf("LOST", "FOUND", "FOUND_CASE")
}
