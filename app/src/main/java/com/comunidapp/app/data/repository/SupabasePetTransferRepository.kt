package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.AcceptPetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.CancelPetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.InitiatePetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toDomain
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.RejectPetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferId
import com.comunidapp.app.domain.pets.PetTransferRepository
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.domain.pets.petFailure
import java.time.Instant

/**
 * LeoVer M08 — transfer repository over RPC + SELECT RLS.
 */
class SupabasePetTransferRepository(
    private val remote: PetM08RemoteDataSource = SupabasePetM08RemoteDataSource()
) : PetTransferRepository {

    override suspend fun create(transfer: PetTransfer): Result<PetTransferId> {
        return try {
            val (toPerson, toOrg) = when (val t = transfer.toPrincipal) {
                is PetPrincipalHolder.Person -> t.userId to null
                is PetPrincipalHolder.Organization -> null to t.organizationId.value
            }
            val row = remote.initiateTransfer(
                InitiatePetTransferParams(
                    petId = transfer.petId.value,
                    toPersonId = toPerson,
                    toOrganizationId = toOrg,
                    expiresAt = Instant.ofEpochMilli(transfer.expiresAtEpochMs).toString()
                )
            )
            Result.success(PetTransferId(row.id))
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_TRANSFER_CREATE_FAILED")
        }
    }

    override suspend fun getPending(petId: PetId): PetTransfer? =
        remote.listTransfers(petId.value)
            .map { it.toDomain() }
            .firstOrNull { it.status == PetTransferStatus.PENDING }

    override suspend fun accept(transferId: PetTransferId, atEpochMs: Long): Result<Unit> {
        return try {
            remote.acceptTransfer(AcceptPetTransferParams(transferId.value))
            Result.success(Unit)
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_TRANSFER_ACCEPT_FAILED")
        }
    }

    override suspend fun reject(transferId: PetTransferId, atEpochMs: Long): Result<Unit> {
        return try {
            remote.rejectTransfer(RejectPetTransferParams(transferId.value))
            Result.success(Unit)
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_TRANSFER_REJECT_FAILED")
        }
    }

    override suspend fun cancel(transferId: PetTransferId, atEpochMs: Long): Result<Unit> {
        return try {
            remote.cancelTransfer(CancelPetTransferParams(transferId.value))
            Result.success(Unit)
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_TRANSFER_CANCEL_FAILED")
        }
    }

    override suspend fun expire(transferId: PetTransferId, atEpochMs: Long): Result<Unit> {
        // Client cannot call m08_expire_pet_transfers (server-only). Surface as unsupported.
        return petFailure("PET_TRANSFER_EXPIRE_CLIENT_FORBIDDEN")
    }

    override suspend fun listHistory(petId: PetId): List<PetTransfer> =
        remote.listTransfers(petId.value).map { it.toDomain() }
}
