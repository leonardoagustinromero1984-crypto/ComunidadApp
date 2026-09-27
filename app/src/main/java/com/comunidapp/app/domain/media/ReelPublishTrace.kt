package com.comunidapp.app.domain.media

/**
 * Directed, in-memory Reel publish trace for QA. No tokens, URLs, or payloads.
 */
object ReelPublishTrace {
    enum class Stage {
        SUBMIT_START,
        URI_READY,
        SOURCE_COPY,
        FILE_METADATA_READY,
        MATERIALIZE_READY,
        PREPARE_START,
        PREPARE_DONE,
        REGISTER_START,
        REGISTER_SUCCESS,
        REGISTER_FAIL,
        UPLOAD_START,
        UPLOAD_SUCCESS,
        UPLOAD_FAIL,
        CREATE_REEL_START,
        CREATE_REEL_SUCCESS,
        CREATE_REEL_FAIL,
        ATTACH_PETS,
        VITACORA,
        ASSOCIATE,
        CONFIRM,
        RESOLVE,
        UI_SUCCESS,
        UI_ERROR
    }

    data class Step(
        val stage: Stage,
        val durationMs: Long,
        val fileSize: Long? = null,
        val mime: String? = null,
        val result: String? = null
    )

    @Volatile
    private var startedAt: Long = 0L
    private val steps = java.util.concurrent.CopyOnWriteArrayList<Step>()

    fun begin() {
        startedAt = System.currentTimeMillis()
        steps.clear()
        mark(Stage.SUBMIT_START, result = "OK")
    }

    fun mark(
        stage: Stage,
        fileSize: Long? = null,
        mime: String? = null,
        result: String? = null
    ) {
        val elapsed = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt
        steps += Step(
            stage = stage,
            durationMs = elapsed,
            fileSize = fileSize?.takeIf { it > 0L },
            mime = mime?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
            result = result?.trim()?.takeIf { it.isNotEmpty() }?.take(48)
        )
    }

    fun snapshot(): List<Step> = steps.toList()

    fun lastStage(): Stage? = steps.lastOrNull()?.stage

    fun lastResult(): String? = steps.lastOrNull()?.result

    fun elapsedOf(stage: Stage): Long? = snapshot().lastOrNull { it.stage == stage }?.durationMs

    fun durationBetween(from: Stage, to: Stage): Long? {
        val start = elapsedOf(from) ?: return null
        val end = elapsedOf(to) ?: return null
        return (end - start).coerceAtLeast(0L)
    }

    fun compact(): String = snapshot().joinToString(" > ") { step ->
        buildString {
            append(step.stage.name)
            step.result?.let { append("[").append(it).append("]") }
            step.fileSize?.let { append(" ").append(it).append("B") }
            append("@").append(step.durationMs).append("ms")
        }
    }

    fun userFacingCategory(): String {
        val failed = snapshot().lastOrNull { it.stage.name.endsWith("_FAIL") || it.stage == Stage.UI_ERROR }
        val signal = listOfNotNull(failed?.result, failed?.stage?.name, lastResult())
            .joinToString(" ")
            .uppercase()
        return when {
            "FILE_TOO_LARGE" in signal || "TOO_LARGE" in signal || "SIZE" == failed?.result -> "FILE_TOO_LARGE"
            "RATE_LIMITED" in signal -> "RATE_LIMITED"
            "QUOTA" in signal -> "DAILY_QUOTA"
            "TIMEOUT" in signal -> "TIMEOUT"
            failed?.stage == Stage.UPLOAD_FAIL || "UPLOAD" in signal -> "UPLOAD_FAILED"
            failed?.stage == Stage.REGISTER_FAIL || "REGISTER" in signal -> "REGISTER_FAILED"
            failed?.stage == Stage.CREATE_REEL_FAIL || "CREATE" in signal -> "CREATE_FAILED"
            else -> "PUBLISH_FAILED"
        }
    }

    fun logDebugTimings() {
        if (!com.comunidapp.app.BuildConfig.DEBUG) return
        android.util.Log.i("ReelPublishStage", "trace=${compact()}")
    }

    fun userFacingMessage(): String = when (userFacingCategory()) {
        "FILE_TOO_LARGE" -> "El video supera el tamaño máximo permitido."
        "RATE_LIMITED" -> "Estás publicando demasiado rápido. Intentá más tarde."
        "DAILY_QUOTA" -> "Alcanzaste el límite diario de subidas. Intentá más tarde."
        "TIMEOUT" -> "La publicación tardó demasiado. Intentá de nuevo."
        "UPLOAD_FAILED" -> "No pudimos subir el video. Revisá tu conexión e intentá de nuevo."
        "REGISTER_FAILED" -> "No pudimos registrar el video. Intentá de nuevo."
        "CREATE_FAILED" -> "No pudimos crear el Reel. Intentá de nuevo."
        else -> "No pudimos publicar el Reel. Intentá de nuevo."
    }
}
