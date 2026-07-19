package com.comunidapp.app.data.files

import com.comunidapp.app.domain.files.FileLegacyCompatibility
import com.comunidapp.app.domain.files.LegacyFileReference
import com.comunidapp.app.domain.files.LegacyPublicUrlReference
import com.comunidapp.app.domain.files.LegacyStoragePathReference

/**
 * Puente de lectura para referencias previas a M05.
 * Una URL arbitraria nunca se convierte en un FileAsset confiable.
 */
object LegacyFileReferenceAdapter {

    fun fromPublicUrl(url: String): Result<LegacyPublicUrlReference> =
        FileLegacyCompatibility.parsePublicUrl(url)

    fun fromStoragePath(path: String): Result<LegacyStoragePathReference> =
        FileLegacyCompatibility.parseStoragePath(path)

    fun grantsOwnership(reference: LegacyFileReference): Boolean =
        FileLegacyCompatibility.grantsOwnership(reference)

    fun allowsNewUpload(reference: LegacyFileReference): Boolean =
        FileLegacyCompatibility.allowsUpload(reference)
}
