package com.comunidapp.app.domain.vitacora.import

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VitacoraImportCreatedPet(
    val petId: String,
    val vitacoraNumber: Long,
    val name: String,
    val organizationId: String,
    val externalPetId: String,
    val importId: String,
    val needsPhoto: Boolean = true,
    val adoptionPublished: Boolean = false
)

data class VitacoraImportAuth(
    val actorId: String,
    val isStaff: Boolean,
    val membershipActive: Boolean,
    val permissions: Set<String>,
    val organizationVerified: Boolean,
    val requestedOrganizationId: String?
)

object VitacoraImportAuthorization {
    fun canImport(auth: VitacoraImportAuth, orgId: String, mode: VitacoraImportMode): Result<Unit> {
        if (auth.requestedOrganizationId != null && auth.requestedOrganizationId != orgId) {
            return Result.failure(IllegalStateException("ORG_ESCALATION_DENIED"))
        }
        if (auth.isStaff) return Result.success(Unit)
        if (mode == VitacoraImportMode.ADMIN) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        if (!auth.membershipActive) return Result.failure(IllegalStateException("PENDING_OR_NOT_MEMBER"))
        val permitted = VitacoraImportPolicy.ORG_IMPORT_PERMISSION in auth.permissions ||
            VitacoraImportPolicy.ORG_PETS_MANAGE_PERMISSION in auth.permissions
        if (!permitted) return Result.failure(IllegalStateException("MISSING_PERMISSION"))
        if (!auth.organizationVerified) return Result.failure(IllegalStateException("ORG_NOT_VERIFIED"))
        return Result.success(Unit)
    }
}

class InMemoryVitacoraImportEngine(
    private val locationNodes: List<com.comunidapp.app.data.model.LocationNode>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val jobs = ConcurrentHashMap<String, VitacoraImportJob>()
    private val existingByOrg = ConcurrentHashMap<String, MutableSet<String>>()
    private val created = ConcurrentHashMap<String, VitacoraImportCreatedPet>()
    private val payloads = ConcurrentHashMap<String, VitacoraImportWorkbookPayload>()
    private val numbers = AtomicLong(0)
    private val mutex = Mutex()

    fun publicNumberSequence(): AtomicLong = numbers
    fun createdPets(): Collection<VitacoraImportCreatedPet> = created.values
    fun job(id: String): VitacoraImportJob? = jobs[id]
    fun list(orgId: String? = null, admin: Boolean = false): List<VitacoraImportJob> =
        jobs.values.filter { admin || it.organizationId == orgId }
            .sortedByDescending { it.createdAtEpochMs }

    fun seedExisting(orgId: String, externalId: String) {
        existingByOrg.getOrPut(orgId) { mutableSetOf() } +=
            ExternalPetIdNormalizer.normalize(externalId)!!
    }

    fun nextPublicNumberIgnoringClient(clientValue: Long?): Long {
        // Server assigns; client value is discarded.
        return numbers.incrementAndGet()
    }

    suspend fun createJob(
        orgId: String,
        orgName: String,
        mode: VitacoraImportMode,
        actorId: String,
        auth: VitacoraImportAuth,
        storagePath: String,
        fileSha256: String?,
        comment: String? = null
    ): Result<VitacoraImportJob> {
        VitacoraImportAuthorization.canImport(auth, orgId, mode).onFailure { return Result.failure(it) }
        val status = if (mode == VitacoraImportMode.ASSISTED) {
            VitacoraImportJobStatus.PENDING
        } else {
            VitacoraImportJobStatus.UPLOADED
        }
        val job = VitacoraImportJob(
            id = UUID.randomUUID().toString(),
            organizationId = orgId,
            organizationName = orgName,
            mode = mode,
            status = status,
            initiatedBy = actorId,
            comment = comment,
            storagePath = storagePath,
            fileSha256 = fileSha256,
            createdAtEpochMs = clock()
        )
        jobs[job.id] = job
        return Result.success(job)
    }

    fun rememberPayload(jobId: String, workbook: VitacoraImportWorkbookPayload) {
        payloads[jobId] = workbook
    }

    suspend fun analyzeStored(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob> {
        val payload = payloads[jobId] ?: return Result.failure(IllegalStateException("PAYLOAD_MISSING"))
        return analyze(jobId, payload, auth)
    }

    suspend fun analyze(
        jobId: String,
        workbook: VitacoraImportWorkbookPayload,
        auth: VitacoraImportAuth
    ): Result<VitacoraImportJob> = mutex.withLock {
        val current = jobs[jobId] ?: return Result.failure(IllegalStateException("NOT_FOUND"))
        VitacoraImportAuthorization.canImport(auth, current.organizationId, current.mode)
            .onFailure { return Result.failure(it) }
        if (current.status == VitacoraImportJobStatus.COMPLETED ||
            current.status == VitacoraImportJobStatus.COMPLETED_WITH_ERRORS
        ) {
            return Result.success(current)
        }
        jobs[jobId] = current.copy(status = VitacoraImportJobStatus.ANALYZING)
        val ctx = VitacoraImportAnalyzeContext(
            organizationId = current.organizationId,
            existingNormalizedExternalIds = existingByOrg[current.organizationId].orEmpty(),
            locationNodes = locationNodes,
            verifiedOrganization = auth.organizationVerified || current.mode == VitacoraImportMode.ADMIN,
            actorCanImport = true,
            mode = current.mode
        )
        val (summary, rows) = VitacoraImportAnalyzer.analyze(workbook, ctx)
        val nextStatus = when {
            summary.ready + summary.warnings > 0 -> VitacoraImportJobStatus.READY
            rows.any { it.rowNumber == 0 } -> VitacoraImportJobStatus.FAILED
            else -> VitacoraImportJobStatus.REQUIRES_CORRECTION
        }
        val updated = current.copy(
            status = nextStatus,
            analyzedAtEpochMs = clock(),
            executedBy = auth.actorId,
            summary = summary,
            rows = rows
        )
        jobs[jobId] = updated
        return Result.success(updated)
    }

    suspend fun confirm(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob> = mutex.withLock {
        val current = jobs[jobId] ?: return Result.failure(IllegalStateException("NOT_FOUND"))
        if (current.mode != VitacoraImportMode.SELF_SERVICE && !auth.isStaff) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        if (current.mode == VitacoraImportMode.SELF_SERVICE) {
            VitacoraImportAuthorization.canImport(auth, current.organizationId, current.mode)
                .onFailure { return Result.failure(it) }
        }
        if (current.status == VitacoraImportJobStatus.COMPLETED ||
            current.status == VitacoraImportJobStatus.COMPLETED_WITH_ERRORS
        ) {
            return Result.success(current)
        }
        if (current.status == VitacoraImportJobStatus.IMPORTING) {
            return Result.success(current)
        }
        if (current.status != VitacoraImportJobStatus.READY) {
            return Result.failure(IllegalStateException("NOT_READY"))
        }
        jobs[jobId] = current.copy(status = VitacoraImportJobStatus.IMPORTING)
        val existing = existingByOrg.getOrPut(current.organizationId) { mutableSetOf() }
        val nextRows = current.rows.map { row ->
            if (!row.importable || row.mapped == null) return@map row
            val mapped = row.mapped
            if (mapped.externalPetIdNormalized in existing) {
                return@map row.copy(
                    status = VitacoraImportRowStatus.YA_EXISTE,
                    issues = row.issues + VitacoraImportFieldIssue(
                        VitacoraImportColumns.EXTERNAL_ID,
                        "ALREADY_EXISTS",
                        "Ya existe una mascota con esa referencia en la organización.",
                        "No se actualiza automáticamente."
                    )
                )
            }
            val petId = UUID.randomUUID().toString()
            val number = numbers.incrementAndGet()
            existing += mapped.externalPetIdNormalized
            created[petId] = VitacoraImportCreatedPet(
                petId = petId,
                vitacoraNumber = number,
                name = mapped.name,
                organizationId = current.organizationId,
                externalPetId = mapped.externalPetIdDisplay,
                importId = current.id
            )
            row.copy(
                status = VitacoraImportRowStatus.LISTA,
                createdPetId = petId,
                createdVitacoraNumber = number
            )
        }
        val createdCount = nextRows.count { it.createdPetId != null }
        val failed = nextRows.count { it.status == VitacoraImportRowStatus.ERROR || it.status == VitacoraImportRowStatus.YA_EXISTE }
        val done = current.copy(
            status = if (failed > 0 && createdCount > 0) {
                VitacoraImportJobStatus.COMPLETED_WITH_ERRORS
            } else if (createdCount == 0) {
                VitacoraImportJobStatus.REQUIRES_CORRECTION
            } else {
                VitacoraImportJobStatus.COMPLETED
            },
            confirmedAtEpochMs = clock(),
            completedAtEpochMs = clock(),
            executedBy = auth.actorId,
            rows = nextRows,
            summary = VitacoraImportAnalyzer.summaryOf(nextRows).copy(created = createdCount)
        )
        jobs[jobId] = done
        return Result.success(done)
    }
}

fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
