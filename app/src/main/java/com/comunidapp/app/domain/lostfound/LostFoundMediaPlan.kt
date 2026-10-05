package com.comunidapp.app.domain.lostfound

/**
 * A lost or found photo stays on the case. The feed may point at that same
 * asset. It does not upload a second copy or open a second VitaCora moment.
 */
data class LostFoundMediaReuse(
    val assetId: String?,
    val uploadAgain: Boolean,
    val createVitaCoraMoment: Boolean
)

object LostFoundMediaPlan {
    fun reuse(caseAssetId: String?): LostFoundMediaReuse {
        val id = caseAssetId?.trim()?.takeIf { it.isNotEmpty() }
        return if (id == null) {
            LostFoundMediaReuse(assetId = null, uploadAgain = true, createVitaCoraMoment = false)
        } else {
            LostFoundMediaReuse(assetId = id, uploadAgain = false, createVitaCoraMoment = false)
        }
    }
}
