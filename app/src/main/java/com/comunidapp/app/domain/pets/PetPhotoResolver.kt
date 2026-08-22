package com.comunidapp.app.domain.pets

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.files.FileDisplayResolver
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.shared.media.MediaRef
import com.comunidapp.shared.media.MediaRefParser

/**
 * Foto canónica de mascota: [Pet.avatarFileAssetId] (M05).
 * [Pet.photoUrl] es legacy HTTPS. Nunca persiste signed URLs.
 */
object PetPhotoResolver {

    suspend fun displayUrl(
        pet: Pet?,
        actorUserId: String? = AuthProvider.repository.getCurrentUser()?.id,
        resolver: FileDisplayResolver = DataProvider.fileDisplayResolver
    ): String? {
        if (pet == null) return null
        return when (val ref = MediaRefParser.fromPetFields(pet.avatarFileAssetId, pet.photoUrl)) {
            is MediaRef.RemoteUrl -> ref.url
            is MediaRef.Asset -> when (
                val result = resolver.resolve(
                    assetId = ref.assetId,
                    legacyReference = null,
                    context = FileAuthContext(actorUserId = actorUserId)
                )
            ) {
                is AppResult.Success -> result.data.displayValue
                is AppResult.Failure -> null
            }
            is MediaRef.ProfileAvatarPath -> null
            null -> null
        }
    }

    fun sanitizeObjectPath(path: String): String {
        val parts = path.split('/').filter { it.isNotBlank() }
        return if (parts.size <= 3) path else ".../" + parts.takeLast(3).joinToString("/")
    }
}
