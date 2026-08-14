package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.media.M05MediaUploadGateway
import com.comunidapp.shared.media.mapMediaThrowable
import com.comunidapp.shared.remote.PetsRemoteGateway
import com.comunidapp.shared.remote.PetsWriteKind
import com.comunidapp.shared.remote.RemoteCreatePetParams
import com.comunidapp.shared.remote.RemotePetsMapper
import com.comunidapp.shared.remote.RemoteUpdatePetProfileParams
import com.comunidapp.shared.remote.classifyPetsWrite
import com.comunidapp.shared.remote.mapPetsThrowable
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.ErrorSanitizer
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Mascotas REAL_REMOTE — RPC `m08_list_accessible_pets` + SELECT `pets`.
 * Create: `m08_create_pet_with_principal` + opcional M05 PET_AVATAR / `m08_set_pet_avatar_asset`.
 * Update: `m08_update_pet_profile` + opcional avatar (PartialSuccess).
 * Autorización en backend (RLS / m08_actor_can_read_pet).
 */
internal class RemoteSharedPetsRepository(
    private val gateway: PetsRemoteGateway,
    private val sessionRepository: SessionRepository,
    private val mediaUploadGateway: M05MediaUploadGateway? = null
) : SharedPetsRepository {
    override val dataMode: PetsDataMode = PetsDataMode.REAL_REMOTE

    private val refreshTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyPets(userId: String): Flow<VerticalLoadState<List<PetSummary>>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                emit(loadList(userId))
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePetDetail(petId: PetId): Flow<VerticalLoadState<PetDetailView>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                val session = sessionRepository.currentSession()
                if (session !is SessionState.Authenticated) {
                    emit(VerticalLoadState.Error("Tu sesión no está disponible."))
                    return@flow
                }
                val result = gateway.fetchPetById(petId.value)
                result.fold(
                    onSuccess = { row ->
                        if (row == null) {
                            emit(VerticalLoadState.Error("No encontramos ese contenido."))
                        } else {
                            emit(VerticalLoadState.Content(RemotePetsMapper.toDetail(row)))
                        }
                    },
                    onFailure = { emit(VerticalLoadState.Error(mapPetsThrowable(it))) }
                )
            }
        }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    override suspend fun create(draft: PetCreateDraft): PetCreateResult {
        PetCreateDraftValidator.validate(draft).exceptionOrNull()?.let {
            return PetCreateResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return PetCreateResult.Unauthenticated("Tu sesión no está disponible.")
        }
        val created = gateway.createPetWithPrincipal(
            RemoteCreatePetParams(
                name = draft.name.trim(),
                species = draft.species.trim().ifBlank { "UNKNOWN" },
                sex = draft.sex.trim().ifBlank { "UNKNOWN" },
                size = draft.size.trim().ifBlank { "UNKNOWN" },
                description = draft.description.trim()
            )
        ).getOrElse { return mapCreateResult(it) }

        refreshTick.update { it + 1 }

        val avatarFile = draft.avatarFile
        if (avatarFile == null) {
            return PetCreateResult.Success(id = PetId(created.id), avatarAttached = false)
        }

        val media = mediaUploadGateway
        if (media == null) {
            return PetCreateResult.PartialSuccess(
                id = PetId(created.id),
                mediaMessage = "La mascota se creó, pero no se pudo adjuntar la foto."
            )
        }

        val assetId = media.uploadPetAvatar(
            petId = created.id,
            actorUserId = session.user.userId,
            file = avatarFile
        ).getOrElse {
            return PetCreateResult.PartialSuccess(
                id = PetId(created.id),
                mediaMessage = mapMediaThrowable(it)
            )
        }

        val avatarSet = gateway.setPetAvatarAsset(created.id, assetId)
        if (avatarSet.isFailure) {
            return PetCreateResult.PartialSuccess(
                id = PetId(created.id),
                mediaMessage = mapPetsThrowable(
                    avatarSet.exceptionOrNull() ?: IllegalStateException("PET_AVATAR_SET_FAILED")
                )
            )
        }

        refreshTick.update { it + 1 }
        return PetCreateResult.Success(id = PetId(created.id), avatarAttached = true)
    }

    override suspend fun update(petId: PetId, draft: PetEditDraft): PetEditResult {
        PetEditDraftValidator.validate(draft).exceptionOrNull()?.let {
            return PetEditResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return PetEditResult.Unauthenticated("Tu sesión no está disponible.")
        }
        gateway.updatePetProfile(
            RemoteUpdatePetProfileParams(
                petId = petId.value,
                name = draft.name.trim(),
                species = draft.species.trim().ifBlank { "UNKNOWN" },
                breed = draft.breed?.trim()?.takeIf { it.isNotEmpty() },
                sex = draft.sex.trim().ifBlank { "UNKNOWN" },
                size = draft.size.trim().ifBlank { "UNKNOWN" },
                description = draft.description.trim(),
                ageYears = draft.ageYears,
                ageMonths = draft.ageMonths,
                color = draft.color?.trim()?.takeIf { it.isNotEmpty() },
                microchipId = null
            )
        ).getOrElse { return mapEditResult(it) }

        refreshTick.update { it + 1 }

        val avatarFile = draft.avatarFile
        if (avatarFile == null) {
            return PetEditResult.Success(id = petId, avatarAttached = false)
        }

        val media = mediaUploadGateway
        if (media == null) {
            return PetEditResult.PartialSuccess(
                id = petId,
                mediaMessage = "Los datos se guardaron, pero no se pudo adjuntar la foto."
            )
        }

        val assetId = media.uploadPetAvatar(
            petId = petId.value,
            actorUserId = session.user.userId,
            file = avatarFile
        ).getOrElse {
            return PetEditResult.PartialSuccess(
                id = petId,
                mediaMessage = mapMediaThrowable(it)
            )
        }

        val avatarSet = gateway.setPetAvatarAsset(petId.value, assetId)
        if (avatarSet.isFailure) {
            return PetEditResult.PartialSuccess(
                id = petId,
                mediaMessage = mapPetsThrowable(
                    avatarSet.exceptionOrNull() ?: IllegalStateException("PET_AVATAR_SET_FAILED")
                )
            )
        }

        refreshTick.update { it + 1 }
        return PetEditResult.Success(id = petId, avatarAttached = true)
    }

    private fun mapCreateResult(t: Throwable): PetCreateResult {
        val msg = mapPetsThrowable(t)
        return when (classifyPetsWrite(t)) {
            PetsWriteKind.UNAUTHENTICATED -> PetCreateResult.Unauthenticated(msg)
            PetsWriteKind.FORBIDDEN -> PetCreateResult.Forbidden(msg)
            PetsWriteKind.CONFLICT -> PetCreateResult.Conflict(msg)
            PetsWriteKind.VALIDATION -> PetCreateResult.ValidationError(msg)
            PetsWriteKind.BACKEND -> PetCreateResult.BackendError(msg)
        }
    }

    private fun mapEditResult(t: Throwable): PetEditResult {
        val msg = mapPetsThrowable(t)
        return when (classifyPetsWrite(t)) {
            PetsWriteKind.UNAUTHENTICATED -> PetEditResult.Unauthenticated(msg)
            PetsWriteKind.FORBIDDEN -> PetEditResult.Forbidden(msg)
            PetsWriteKind.CONFLICT,
            PetsWriteKind.BACKEND -> PetEditResult.BackendError(msg)
            PetsWriteKind.VALIDATION -> PetEditResult.ValidationError(msg)
        }
    }

    private suspend fun loadList(userId: String): VerticalLoadState<List<PetSummary>> {
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return VerticalLoadState.Error("Tu sesión no está disponible.")
        }
        if (session.user.userId != userId) {
            return VerticalLoadState.Error("No tenés permiso para ver estas mascotas.")
        }
        return gateway.listAccessibleActivePets().fold(
            onSuccess = { rows ->
                if (rows.isEmpty()) VerticalLoadState.Empty
                else VerticalLoadState.Content(rows.map(RemotePetsMapper::toSummary))
            },
            onFailure = { VerticalLoadState.Error(mapPetsThrowable(it)) }
        )
    }
}

internal class UnconfiguredSharedPetsRepository : SharedPetsRepository {
    override val dataMode: PetsDataMode = PetsDataMode.REAL_REMOTE

    override fun observeMyPets(userId: String): Flow<VerticalLoadState<List<PetSummary>>> = flow {
        emit(VerticalLoadState.Loading)
        emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override fun observePetDetail(petId: PetId): Flow<VerticalLoadState<PetDetailView>> = flow {
        emit(VerticalLoadState.Loading)
        emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override suspend fun refresh() = Unit

    override suspend fun create(draft: PetCreateDraft): PetCreateResult =
        PetCreateResult.BackendError("Servicio no configurado.")

    override suspend fun update(petId: PetId, draft: PetEditDraft): PetEditResult =
        PetEditResult.BackendError("Servicio no configurado.")
}
