package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.authorization.FileAccessDecision
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.files.authorization.FileAuthorization

class SupabaseFileAccessRepository(
    private val assets: FileAssetRepository = SupabaseFileAssetRepository()
) : FileAccessRepository {

    override suspend fun canRead(
        context: FileAuthContext,
        assetId: String
    ): AppResult<FileAccessDecision> =
        withAsset(assetId) { FileAuthorization.canRead(context, it) }

    override suspend fun canUpload(
        context: FileAuthContext,
        purpose: FileAssetPurpose,
        owner: FileAssetOwner
    ): AppResult<FileAccessDecision> =
        AppResult.Success(FileAuthorization.canUpload(context, purpose, owner))

    override suspend fun canReplace(
        context: FileAuthContext,
        assetId: String
    ): AppResult<FileAccessDecision> =
        withAsset(assetId) { FileAuthorization.canReplace(context, it) }

    override suspend fun canDelete(
        context: FileAuthContext,
        assetId: String,
        nowEpochMs: Long
    ): AppResult<FileAccessDecision> =
        withAsset(assetId) {
            FileAuthorization.canDelete(
                context = context,
                asset = it,
                activeLinkCount = 0,
                nowEpochMs = nowEpochMs
            )
        }

    private suspend fun withAsset(
        assetId: String,
        decision: (com.comunidapp.app.domain.files.FileAsset) -> FileAccessDecision
    ): AppResult<FileAccessDecision> =
        when (val result = assets.getAsset(assetId)) {
            is AppResult.Success -> AppResult.Success(decision(result.data))
            is AppResult.Failure -> result
        }
}
