package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.data.model.M14PassportHistory

/**
 * Lost publish writes two real records for the same photo: the care moment from
 * the case attachment, and the social moment created when the alert is also a post.
 * The history keeps one of them and prefers the care moment over the generic post.
 * Rows that do not share a photo stay as they are.
 */
object VitaCoraHistoryDuplicates {
    fun collapse(items: List<M14PassportHistory>): List<M14PassportHistory> {
        val indexByKey = mutableMapOf<String, Int>()
        val result = mutableListOf<M14PassportHistory>()
        items.forEach { item ->
            val key = mediaKey(item)
            if (key.isEmpty()) {
                result += item
                return@forEach
            }
            val existing = indexByKey[key]
            if (existing == null) {
                indexByKey[key] = result.size
                result += item
            } else if (isGenericSocial(result[existing]) && !isGenericSocial(item)) {
                result[existing] = item
            }
        }
        return result
    }

    private fun mediaKey(item: M14PassportHistory): String =
        item.mediaDisplayUrl?.trim().orEmpty().ifBlank {
            item.mediaDisplayUrls.firstOrNull()?.trim().orEmpty()
        }

    private fun isGenericSocial(item: M14PassportHistory): Boolean {
        val event = item.metadataEvent?.trim().orEmpty()
        val reason = item.reason?.trim().orEmpty()
        return event.equals("SOCIAL", ignoreCase = true) ||
            reason.equals("Publicación en VitaCora", ignoreCase = true)
    }
}
