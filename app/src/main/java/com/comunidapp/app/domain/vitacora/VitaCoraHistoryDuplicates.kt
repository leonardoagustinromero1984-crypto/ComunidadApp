package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.data.model.M14PassportHistory

/**
 * History shows the rows the case actually stored. A lost photo is not copied
 * into a second VitaCora moment at publish time, so this list is not collapsed.
 */
object VitaCoraHistoryDuplicates {
    fun collapse(items: List<M14PassportHistory>): List<M14PassportHistory> = items
}
