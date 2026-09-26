package com.comunidapp.app.domain.social

import kotlinx.serialization.Serializable

@Serializable
enum class PendingSocialPublishState {
    PREPARING,
    UPLOADING,
    PUBLISHING,
    SUCCESS,
    FAILED,
    CANCELLED
}

@Serializable
enum class ReelPublishPhase {
    PREPARE,
    UPLOAD,
    REGISTER,
    CREATE,
    ATTACH_PETS,
    VITACORA,
    DONE
}

@Serializable
data class PendingSocialPublish(
    val jobId: String,
    val contentKind: String = "REEL",
    val state: PendingSocialPublishState = PendingSocialPublishState.PREPARING,
    val progressPercent: Int? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val actorUserId: String,
    val caption: String,
    val locationText: String? = null,
    val localityId: String? = null,
    val compositionJson: String? = null,
    val petIds: List<String> = emptyList(),
    val saveToVitaCora: Boolean = false,
    val vitaCoraResolved: Boolean = false,
    val sourcePath: String? = null,
    val uploadPath: String? = null,
    val mimeType: String = VideoExportPolicy.TARGET_VIDEO_MIME,
    val filename: String = "reel.mp4",
    val sizeBytes: Long = 0L,
    val exportDecision: String? = null,
    val errorCategory: String? = null,
    val backendPostId: String? = null,
    val assetId: String? = null,
    val sessionId: String? = null,
    val versionId: String? = null,
    val physicalBucket: String? = null,
    val storagePath: String? = null,
    val logicalBucket: String? = null,
    val registerCompleted: Boolean = false,
    val uploadCompleted: Boolean = false,
    val createStarted: Boolean = false,
    val createCompleted: Boolean = false,
    val petsAttached: Boolean = false,
    val createUncertain: Boolean = false,
    val completedPhases: List<String> = emptyList(),
    val lastErrorPhase: String? = null,
    val bytesUploaded: Long? = null,
    val totalBytes: Long? = null
) {
    val isActive: Boolean
        get() = state == PendingSocialPublishState.PREPARING ||
            state == PendingSocialPublishState.UPLOADING ||
            state == PendingSocialPublishState.PUBLISHING

    val reelCreated: Boolean
        get() = createCompleted || !backendPostId.isNullOrBlank()

    val canCancel: Boolean
        get() = state == PendingSocialPublishState.PREPARING ||
            state == PendingSocialPublishState.UPLOADING

    val canRetry: Boolean
        get() = state == PendingSocialPublishState.FAILED &&
            errorCategory != "FILE_TOO_LARGE"

    val canDismiss: Boolean
        get() = !isActive

    val shouldKeepFiles: Boolean
        get() = isActive || (state == PendingSocialPublishState.FAILED && canRetry)

    val shouldKeepForResume: Boolean
        get() = isActive || canRetry

    fun belongsTo(userId: String?): Boolean =
        !userId.isNullOrBlank() && actorUserId == userId

    fun nextPhase(): ReelPublishPhase = when {
        state == PendingSocialPublishState.SUCCESS ||
            state == PendingSocialPublishState.CANCELLED -> ReelPublishPhase.DONE
        !uploadCompleted || assetId.isNullOrBlank() -> when {
            exportDecision == null && !registerCompleted -> ReelPublishPhase.PREPARE
            !registerCompleted -> ReelPublishPhase.REGISTER
            else -> ReelPublishPhase.UPLOAD
        }
        !reelCreated -> ReelPublishPhase.CREATE
        petIds.isNotEmpty() && !petsAttached -> ReelPublishPhase.ATTACH_PETS
        petIds.isNotEmpty() && !vitaCoraResolved -> ReelPublishPhase.VITACORA
        else -> ReelPublishPhase.DONE
    }

    fun withPhaseCompleted(phase: ReelPublishPhase): PendingSocialPublish {
        val name = phase.name
        return copy(completedPhases = if (name in completedPhases) completedPhases else completedPhases + name)
    }

    fun phaseMessage(): String = when (state) {
        PendingSocialPublishState.PREPARING -> "Preparando video…"
        PendingSocialPublishState.UPLOADING -> {
            val percent = progressPercent
            if (percent != null) "Subiendo video… $percent %" else "Subiendo video…"
        }
        PendingSocialPublishState.PUBLISHING -> "Publicando…"
        PendingSocialPublishState.SUCCESS -> "Clip publicado"
        PendingSocialPublishState.FAILED -> PendingSocialPublishErrors.bannerMessage(errorCategory, reelCreated)
        PendingSocialPublishState.CANCELLED -> "Publicación cancelada"
    }
}

object PendingSocialPublishErrors {
    const val TRANSCODE_FAILED = "TRANSCODE_FAILED"
    const val UPLOAD_FAILED = "UPLOAD_FAILED"
    const val FILE_TOO_LARGE = "FILE_TOO_LARGE"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val DAILY_QUOTA = "DAILY_QUOTA"
    const val REGISTER_FAILED = "REGISTER_FAILED"
    const val CREATE_FAILED = "CREATE_FAILED"
    const val LINK_FAILED = "LINK_FAILED"
    const val TIMEOUT = "TIMEOUT"

    fun fromBlob(blob: String, reelAlreadyCreated: Boolean = false): String {
        val signal = blob.uppercase()
        if (reelAlreadyCreated) {
            return when {
                "FILE_TOO_LARGE" in signal || "TOO_LARGE" in signal || "MEDIA-SIZE" in signal -> FILE_TOO_LARGE
                "RATE_LIMITED" in signal -> RATE_LIMITED
                "QUOTA" in signal -> DAILY_QUOTA
                else -> LINK_FAILED
            }
        }
        return when {
            "FILE_TOO_LARGE" in signal || "TOO_LARGE" in signal || "MEDIA-SIZE" in signal -> FILE_TOO_LARGE
            "RATE_LIMITED" in signal -> RATE_LIMITED
            "QUOTA" in signal -> DAILY_QUOTA
            "TIMEOUT" in signal -> TIMEOUT
            "TRANSCODE" in signal || "TRANSFORMER" in signal -> TRANSCODE_FAILED
            "REGISTER" in signal -> REGISTER_FAILED
            "LINK" in signal || "VITACORA" in signal || "ATTACH" in signal -> LINK_FAILED
            "CREATE" in signal -> CREATE_FAILED
            "UPLOAD" in signal -> UPLOAD_FAILED
            else -> UPLOAD_FAILED
        }
    }

    fun bannerMessage(category: String?, reelAlreadyCreated: Boolean): String =
        if (category == LINK_FAILED || (reelAlreadyCreated && category != FILE_TOO_LARGE)) {
            "El Reel se publicó, pero no pudimos terminar de vincularlo con la mascota."
        } else {
            "No pudimos publicar el Clip"
        }

    fun userMessage(category: String?): String = when (category) {
        FILE_TOO_LARGE -> "El video supera el tamaño máximo permitido."
        RATE_LIMITED -> "Estás publicando demasiado rápido. Intentá más tarde."
        DAILY_QUOTA -> "Alcanzaste el límite diario de subidas. Intentá más tarde."
        TIMEOUT -> "La publicación tardó demasiado. Intentá de nuevo."
        TRANSCODE_FAILED -> "No pudimos procesar el video. Intentá nuevamente."
        REGISTER_FAILED -> "No pudimos registrar el video. Intentá de nuevo."
        CREATE_FAILED -> "No pudimos crear el Reel. Intentá de nuevo."
        LINK_FAILED -> "El Reel se publicó, pero no pudimos terminar de vincularlo con la mascota."
        else -> "No pudimos subir el video. Revisá tu conexión e intentá de nuevo."
    }
}
