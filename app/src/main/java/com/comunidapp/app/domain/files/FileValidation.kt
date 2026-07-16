package com.comunidapp.app.domain.files

object FileNameSanitizer {

    const val MAX_FILENAME_LENGTH = 120

    private val CONTROL = Regex("[\\x00-\\x1F\\x7F]")
    private val MULTI_SPACE = Regex("\\s+")

    val DANGEROUS_EXTENSIONS = setOf(
        "exe", "apk", "bat", "cmd", "com", "msi", "jar", "js",
        "html", "htm", "svg", "sh", "ps1", "dll", "scr"
    )

    fun sanitize(originalFilename: String): Result<String> {
        var name = originalFilename.trim()
        if (name.isEmpty()) return fileFailure("FILENAME_REQUIRED")
        if (name.contains("..")) return fileFailure("FILENAME_TRAVERSAL")
        // eliminar path
        name = name.substringAfterLast('/').substringAfterLast('\\')
        if (name.contains("..")) return fileFailure("FILENAME_TRAVERSAL")
        if (CONTROL.containsMatchIn(name)) return fileFailure("FILENAME_CONTROL_CHARS")
        name = MULTI_SPACE.replace(name, " ").trim()
        if (name.isEmpty()) return fileFailure("FILENAME_EMPTY")
        val lower = name.lowercase()
        val parts = lower.split('.')
        if (parts.size >= 3) {
            // doble extensión: cualquier segmento intermedio peligroso, o final peligroso tras otra ext
            val middle = parts.drop(1).dropLast(1)
            val last = parts.last()
            if (middle.any { it in DANGEROUS_EXTENSIONS } || last in DANGEROUS_EXTENSIONS) {
                return fileFailure("FILENAME_DOUBLE_DANGEROUS")
            }
        }
        val ext = extensionOf(name)
        if (ext != null && ext in DANGEROUS_EXTENSIONS) {
            return fileFailure("FILENAME_DANGEROUS_EXT")
        }
        if (name.length > MAX_FILENAME_LENGTH) {
            val e = ext?.let { ".$it" }.orEmpty()
            val base = name.dropLast(e.length).take(MAX_FILENAME_LENGTH - e.length)
            name = base + e
        }
        // safe: solo alfanumérico, punto, guión, underscore
        val safe = buildString {
            name.forEach { c ->
                when {
                    c.isLetterOrDigit() || c == '.' || c == '-' || c == '_' -> append(c)
                    c == ' ' -> append('_')
                    else -> append('_')
                }
            }
        }.trim('_')
        if (safe.isEmpty() || safe == "." || safe.startsWith(".")) {
            return fileFailure("FILENAME_INVALID")
        }
        return Result.success(safe)
    }

    fun extensionOf(filename: String): String? {
        val base = filename.substringAfterLast('/').substringAfterLast('\\')
        val idx = base.lastIndexOf('.')
        if (idx <= 0 || idx == base.lastIndex) return null
        return base.substring(idx + 1).lowercase()
    }
}

object FileValidationRules {

    fun validateUploadRequest(
        request: FileUploadRequest,
        existingCountForResource: Int = 0
    ): Result<Unit> {
        if (!FilePurposePolicy.acceptsNewUpload(request.purpose)) {
            return fileFailure("PURPOSE_UPLOAD_DENIED")
        }
        FileOwnershipRules.validate(request.owner).getOrElse { return Result.failure(it) }
        FileOwnershipRules.compatibleWithPurpose(request.owner, request.purpose)
            .getOrElse { return Result.failure(it) }
        FileVisibilityRules.validate(request.purpose, request.requestedVisibility)
            .getOrElse { return Result.failure(it) }
        val safe = FileNameSanitizer.sanitize(request.originalFilename)
            .getOrElse { return Result.failure(it) }
        validateMimeAndExtension(
            purpose = request.purpose,
            safeFilename = safe,
            declaredMimeType = request.declaredMimeType,
            detectedMimeType = null,
            sizeBytes = request.sizeBytes
        ).getOrElse { return Result.failure(it) }
        val maxCount = FilePurposePolicy.spec(request.purpose).maxCountPerResource
        if (existingCountForResource >= maxCount) {
            return fileFailure("COUNT_EXCEEDED")
        }
        return Result.success(Unit)
    }

    fun validateMimeAndExtension(
        purpose: FileAssetPurpose,
        safeFilename: String,
        declaredMimeType: String?,
        detectedMimeType: String?,
        sizeBytes: Long
    ): Result<Unit> {
        val spec = FilePurposePolicy.spec(purpose)
        if (spec.maxSizeBytes <= 0L) return fileFailure("PURPOSE_NOT_CONFIGURED")
        if (sizeBytes <= 0L) return fileFailure("SIZE_INVALID")
        if (sizeBytes > spec.maxSizeBytes) return fileFailure("SIZE_EXCEEDED")
        val ext = FileNameSanitizer.extensionOf(safeFilename)
            ?: return fileFailure("EXTENSION_REQUIRED")
        if (ext in FileNameSanitizer.DANGEROUS_EXTENSIONS) {
            return fileFailure("EXTENSION_DANGEROUS")
        }
        if (ext !in spec.allowedExtensions) return fileFailure("EXTENSION_NOT_ALLOWED")
        val effective = detectedMimeType?.trim()?.takeIf { it.isNotEmpty() }
            ?: declaredMimeType?.trim()?.takeIf { it.isNotEmpty() }
            ?: return fileFailure("MIME_REQUIRED")
        val mime = effective.lowercase()
        if (mime !in spec.allowedMimeTypes) return fileFailure("MIME_NOT_ALLOWED")
        // mismatch MIME/extensión peligroso
        if (!mimeMatchesExtension(mime, ext)) {
            return fileFailure("MIME_EXTENSION_MISMATCH")
        }
        // detected prevalece: si ambos existen y difieren de forma peligrosa
        val declared = declaredMimeType?.trim()?.lowercase()
        val detected = detectedMimeType?.trim()?.lowercase()
        if (!declared.isNullOrEmpty() && !detected.isNullOrEmpty() && declared != detected) {
            if (detected !in spec.allowedMimeTypes) return fileFailure("DETECTED_MIME_REJECTED")
            // se usa detected; declarado ignorado
        }
        return Result.success(Unit)
    }

    fun mimeMatchesExtension(mime: String, ext: String): Boolean = when (mime) {
        "image/jpeg" -> ext == "jpg" || ext == "jpeg"
        "image/png" -> ext == "png"
        "image/webp" -> ext == "webp"
        "application/pdf" -> ext == "pdf"
        else -> false
    }

    /** Capacidad futura: no implementada. */
    fun imageProcessingAvailable(): Boolean = false

    fun antivirusAvailable(): Boolean = false

    fun exifStripAvailable(): Boolean = false

    fun thumbnailAvailable(): Boolean = false
}

object FileUploadSessionRules {

    fun validateProgress(percent: Int): Result<Int> {
        if (percent !in 0..100) return fileFailure("PROGRESS_INVALID")
        return Result.success(percent)
    }

    fun canComplete(session: FileUploadSession, versionReady: Boolean): Result<Unit> {
        if (session.state == FileUploadSessionState.CANCELLED) {
            return fileFailure("SESSION_CANCELLED")
        }
        if (session.state == FileUploadSessionState.EXPIRED) {
            return fileFailure("SESSION_EXPIRED")
        }
        if (session.state == FileUploadSessionState.COMPLETED) {
            return fileFailure("SESSION_ALREADY_COMPLETED")
        }
        if (!versionReady) return fileFailure("VERSION_NOT_READY")
        return Result.success(Unit)
    }

    fun cancelIdempotent(session: FileUploadSession, nowEpochMs: Long): FileUploadSession {
        if (session.state == FileUploadSessionState.CANCELLED ||
            session.state == FileUploadSessionState.COMPLETED
        ) {
            return session
        }
        return session.copy(
            state = FileUploadSessionState.CANCELLED,
            failureCode = null
        ).also { nowEpochMs }
    }

    fun rejectIfExpired(session: FileUploadSession, nowEpochMs: Long): Result<Unit> {
        val exp = session.expiresAtEpochMs ?: return Result.success(Unit)
        if (nowEpochMs > exp || session.state == FileUploadSessionState.EXPIRED) {
            return fileFailure("SESSION_EXPIRED")
        }
        return Result.success(Unit)
    }

    fun rejectDoubleSubmit(
        session: FileUploadSession,
        attemptingState: FileUploadSessionState
    ): Result<Unit> {
        if (session.state == FileUploadSessionState.UPLOADING &&
            attemptingState == FileUploadSessionState.UPLOADING
        ) {
            return fileFailure("UPLOAD_IN_PROGRESS")
        }
        if (session.state == FileUploadSessionState.COMPLETED) {
            return fileFailure("SESSION_ALREADY_COMPLETED")
        }
        return Result.success(Unit)
    }
}

object FileSignedAccessRules {

    const val STANDARD_PRIVATE_MAX_SECONDS = 60 * 60
    const val SENSITIVE_SHORT_MAX_SECONDS = 10 * 60
    const val SENSITIVE_SHORT_RECOMMENDED_SECONDS = 5 * 60

    fun ttlSeconds(ttlClass: FileSignedTtlClass): Int = when (ttlClass) {
        FileSignedTtlClass.PUBLIC_RESOLUTION -> STANDARD_PRIVATE_MAX_SECONDS
        FileSignedTtlClass.STANDARD_PRIVATE -> STANDARD_PRIVATE_MAX_SECONDS
        FileSignedTtlClass.SENSITIVE_SHORT -> SENSITIVE_SHORT_RECOMMENDED_SECONDS
    }

    fun validateRequest(
        asset: FileAsset,
        ttlClass: FileSignedTtlClass
    ): Result<Unit> {
        if (asset.status == FileAssetStatus.DELETED) {
            return fileFailure("ASSET_DELETED")
        }
        if (asset.status != FileAssetStatus.READY &&
            ttlClass != FileSignedTtlClass.PUBLIC_RESOLUTION
        ) {
            // allow draft deny for signed
            if (asset.status != FileAssetStatus.READY) return fileFailure("ASSET_NOT_READY")
        }
        when (ttlClass) {
            FileSignedTtlClass.PUBLIC_RESOLUTION -> {
                if (asset.visibility != FileAssetVisibility.PUBLIC) {
                    return fileFailure("PUBLIC_RESOLUTION_REQUIRES_PUBLIC")
                }
                if (FilePurposePolicy.isSensitive(asset.purpose)) {
                    return fileFailure("SENSITIVE_NO_PUBLIC_RESOLUTION")
                }
            }
            FileSignedTtlClass.SENSITIVE_SHORT -> {
                if (!FilePurposePolicy.isSensitive(asset.purpose) &&
                    asset.visibility != FileAssetVisibility.SIGNED_LINK_ONLY &&
                    asset.visibility != FileAssetVisibility.AUTHORIZED_STAFF
                ) {
                    // allowed for sensitive or signed-link assets; non-sensitive may still use short TTL
                }
            }
            FileSignedTtlClass.STANDARD_PRIVATE -> Unit
        }
        return Result.success(Unit)
    }

    fun mustNotPersistUrl(): Boolean = true

    fun mustNotLogToken(): Boolean = true

    fun clampTtl(ttlClass: FileSignedTtlClass, requestedSeconds: Int): Int {
        val max = when (ttlClass) {
            FileSignedTtlClass.SENSITIVE_SHORT -> SENSITIVE_SHORT_MAX_SECONDS
            else -> STANDARD_PRIVATE_MAX_SECONDS
        }
        return requestedSeconds.coerceIn(1, max)
    }
}

object FileRetentionRules {

    fun defaultPolicy(purpose: FileAssetPurpose): FileRetentionPolicy {
        val sensitive = FilePurposePolicy.isSensitive(purpose)
        return FileRetentionPolicy(
            purpose = purpose,
            retainDays = if (sensitive) 365 else null,
            legalHoldAllowed = sensitive,
            requiresAuditOnDelete = sensitive ||
                FilePurposePolicy.spec(purpose).requiresRetention
        )
    }

    fun canPhysicallyDelete(
        asset: FileAsset,
        activeLinkCount: Int,
        nowEpochMs: Long,
        policy: FileRetentionPolicy = defaultPolicy(asset.purpose)
    ): Result<Unit> {
        if (activeLinkCount > 0) return fileFailure("HAS_ACTIVE_LINKS")
        if (asset.legalHold) return fileFailure("LEGAL_HOLD")
        val until = asset.retentionUntilEpochMs
        if (until != null && until > nowEpochMs) return fileFailure("RETENTION_ACTIVE")
        if (policy.retainDays != null && asset.deletedAtEpochMs != null) {
            val minKeep = asset.deletedAtEpochMs + policy.retainDays * 24L * 60L * 60L * 1000L
            if (nowEpochMs < minKeep) return fileFailure("RETENTION_WINDOW")
        }
        return Result.success(Unit)
    }

    fun unlinkDoesNotDeletePhysical(): Boolean = true
}
