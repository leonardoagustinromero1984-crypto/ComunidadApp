package com.comunidapp.app.data.repository

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.argentinaLocationSeed
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.vitacora.import.InMemoryVitacoraImportEngine
import com.comunidapp.app.domain.vitacora.import.VitacoraImportAuth
import com.comunidapp.app.domain.vitacora.import.VitacoraImportJob
import com.comunidapp.app.domain.vitacora.import.VitacoraImportJobStatus
import com.comunidapp.app.domain.vitacora.import.VitacoraImportMode
import com.comunidapp.app.domain.vitacora.import.VitacoraImportOrgHit
import com.comunidapp.app.domain.vitacora.import.VitacoraImportPolicy
import com.comunidapp.app.domain.vitacora.import.VitacoraImportRowAnalysis
import com.comunidapp.app.domain.vitacora.import.VitacoraImportRowStatus
import com.comunidapp.app.domain.vitacora.import.VitacoraImportSummary
import com.comunidapp.app.domain.vitacora.import.VitacoraImportXlsx
import com.comunidapp.app.domain.vitacora.import.sha256Hex
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

interface VitacoraImportRepository {
    suspend fun createAndAnalyze(
        orgId: String,
        orgName: String,
        mode: VitacoraImportMode,
        bytes: ByteArray,
        fileName: String,
        comment: String?,
        auth: VitacoraImportAuth
    ): Result<VitacoraImportJob>

    suspend fun confirm(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob>
    suspend fun analyze(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob>
    suspend fun get(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob>
    suspend fun list(orgId: String?, status: String?, auth: VitacoraImportAuth): Result<List<VitacoraImportJob>>
    suspend fun searchOrganizations(query: String): Result<List<VitacoraImportOrgHit>>
    suspend fun searchByPublicNumber(query: String): Result<List<Pet>>
    fun templateBytes(): ByteArray = VitacoraImportXlsx.writeTemplate()
}

class MockVitacoraImportRepository(
    private val engine: InMemoryVitacoraImportEngine = InMemoryVitacoraImportEngine(argentinaLocationSeed())
) : VitacoraImportRepository {
    override suspend fun createAndAnalyze(
        orgId: String,
        orgName: String,
        mode: VitacoraImportMode,
        bytes: ByteArray,
        fileName: String,
        comment: String?,
        auth: VitacoraImportAuth
    ): Result<VitacoraImportJob> {
        val job = engine.createJob(
            orgId, orgName, mode, auth.actorId, auth,
            storagePath = "mock/${UUID.randomUUID()}.xlsx",
            fileSha256 = sha256Hex(bytes),
            comment = comment
        ).getOrElse { return Result.failure(it) }
        val inspected = VitacoraImportXlsx.inspect(bytes, fileName)
        engine.rememberPayload(job.id, inspected)
        if (mode == VitacoraImportMode.ASSISTED) return Result.success(job)
        return engine.analyze(job.id, inspected, auth)
    }

    override suspend fun confirm(jobId: String, auth: VitacoraImportAuth) = engine.confirm(jobId, auth)
    override suspend fun analyze(jobId: String, auth: VitacoraImportAuth) = engine.analyzeStored(jobId, auth)
    override suspend fun get(jobId: String, auth: VitacoraImportAuth) =
        engine.job(jobId)?.let { Result.success(it) } ?: Result.failure(IllegalStateException("NOT_FOUND"))
    override suspend fun list(orgId: String?, status: String?, auth: VitacoraImportAuth) =
        Result.success(engine.list(orgId, admin = auth.isStaff).filter { status == null || it.status.name == status })
    override suspend fun searchOrganizations(query: String) = Result.success(emptyList<VitacoraImportOrgHit>())
    override suspend fun searchByPublicNumber(query: String): Result<List<Pet>> {
        val number = com.comunidapp.app.domain.vitacora.import.VitacoraNumberQuery.parse(query)
            ?: return Result.success(emptyList())
        return Result.success(
            engine.createdPets().filter { it.vitacoraNumber == number }.map {
                Pet(
                    id = it.petId,
                    name = it.name,
                    species = PetSpecies.DOG,
                    sex = PetSex.UNKNOWN,
                    ageYears = 0,
                    size = PetSize.MEDIUM,
                    description = "",
                    publicVitacoraNumber = it.vitacoraNumber,
                    organizationResponsibleId = it.organizationId,
                    organizationExternalPetId = it.externalPetId,
                    photoUrl = null
                )
            }
        )
    }
}

class CanonicalVitacoraImportRepository(
    private val http: HttpClient = HttpClient(Android) { expectSuccess = false }
) : VitacoraImportRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createAndAnalyze(
        orgId: String,
        orgName: String,
        mode: VitacoraImportMode,
        bytes: ByteArray,
        fileName: String,
        comment: String?,
        auth: VitacoraImportAuth
    ): Result<VitacoraImportJob> = runCatching {
        val inspected = VitacoraImportXlsx.inspect(bytes, fileName)
        if (inspected.rejected != null && inspected.rejected != "TEMPLATE_META_MISSING") {
            error(inspected.rejected)
        }
        if (bytes.size > VitacoraImportPolicy.MAX_FILE_SIZE_BYTES) error("MAX_FILE_SIZE_EXCEEDED")
        val path = "$orgId/${UUID.randomUUID()}.xlsx"
        supabase.storage.from("vitacora-import").upload(path, bytes) {
            upsert = true
            contentType = ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }
        val created = supabase.postgrest.rpc(
            CanonicalBackend.RPC_IMPORT_CREATE_JOB,
            buildJsonObject {
                put("p_organization_id", orgId)
                put("p_mode", mode.name)
                put("p_storage_path", path)
                put("p_file_sha256", sha256Hex(bytes))
                if (comment != null) put("p_comment", comment) else put("p_comment", JsonNull)
            }
        ).decodeAs<String>()
        if (mode != VitacoraImportMode.ASSISTED) invokeAnalyze(created)
        decodeJob(fetchJob(created))
    }

    override suspend fun confirm(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_IMPORT_CONFIRM,
            buildJsonObject { put("p_import_id", jobId) }
        ).decodeAs()
        decodeJob(element.jsonObject)
    }

    override suspend fun analyze(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob> = runCatching {
        invokeAnalyze(jobId)
        decodeJob(fetchJob(jobId))
    }

    override suspend fun get(jobId: String, auth: VitacoraImportAuth): Result<VitacoraImportJob> = runCatching {
        decodeJob(fetchJob(jobId))
    }

    override suspend fun list(
        orgId: String?,
        status: String?,
        auth: VitacoraImportAuth
    ): Result<List<VitacoraImportJob>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_IMPORT_LIST,
            buildJsonObject {
                if (orgId != null) put("p_organization_id", orgId) else put("p_organization_id", JsonNull)
                if (status != null) put("p_status", status) else put("p_status", JsonNull)
            }
        ).decodeAs()
        val arr = element as? JsonArray ?: JsonArray(emptyList())
        arr.map { item ->
            val o = item.jsonObject
            VitacoraImportJob(
                id = o.str("id"),
                organizationId = o.str("organization_id"),
                organizationName = o.strOrNull("organization_name"),
                mode = runCatching { VitacoraImportMode.valueOf(o.str("mode")) }.getOrDefault(VitacoraImportMode.SELF_SERVICE),
                status = runCatching { VitacoraImportJobStatus.valueOf(o.str("status")) }.getOrDefault(VitacoraImportJobStatus.UPLOADED),
                templateVersion = o["template_version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
                initiatedBy = o.strOrNull("initiated_by").orEmpty(),
                executedBy = o.strOrNull("executed_by"),
                comment = o.strOrNull("comment"),
                fileSha256 = o.strOrNull("file_sha256"),
                createdAtEpochMs = parseTs(o.strOrNull("created_at")),
                summary = VitacoraImportSummary(
                    total = o["total_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    ready = o["valid_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    errors = o["failed_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    created = o["created_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                )
            )
        }
    }

    override suspend fun searchOrganizations(query: String): Result<List<VitacoraImportOrgHit>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_SEARCH_ORGS,
            buildJsonObject { put("p_query", query) }
        ).decodeAs()
        (element as? JsonArray ?: JsonArray(emptyList())).map {
            val o = it.jsonObject
            VitacoraImportOrgHit(o.str("id"), o.str("name"), o.str("slug"), o.strOrNull("verification_status") ?: "NOT_REQUESTED")
        }
    }

    override suspend fun searchByPublicNumber(query: String): Result<List<Pet>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_SEARCH_VITACORA_NUMBER,
            buildJsonObject { put("p_query", query) }
        ).decodeAs()
        (element as? JsonArray ?: JsonArray(emptyList())).map {
            val o = it.jsonObject
            Pet(
                id = o.str("pet_id"),
                name = o.str("name"),
                species = PetSpecies.OTHER,
                sex = PetSex.UNKNOWN,
                ageYears = 0,
                size = PetSize.MEDIUM,
                description = "",
                publicVitacoraNumber = o["public_vitacora_number"]?.jsonPrimitive?.longOrNull,
                photoUrl = if (o["needs_photo"]?.jsonPrimitive?.contentOrNull == "true") null else ""
            )
        }
    }

    private suspend fun fetchJob(jobId: String): JsonObject {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_IMPORT_GET,
            buildJsonObject { put("p_import_id", jobId) }
        ).decodeAs()
        return element.jsonObject
    }

    private suspend fun invokeAnalyze(jobId: String) {
        val token = supabase.auth.currentSessionOrNull()?.accessToken
        val base = AppConfigProvider.get().supabaseUrl?.trimEnd('/') ?: BuildConfig.SUPABASE_URL
        val response = http.request("$base/functions/v1/vitacora-import-analyze") {
            method = HttpMethod.Post
            header("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_ANON_KEY}")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody("""{"job_id":"$jobId"}""")
        }
        if (response.status.value !in 200..299) {
            error("ANALYZE_FAILED")
        }
    }

    private fun decodeJob(root: JsonObject): VitacoraImportJob {
        val job = root["job"]?.jsonObject ?: root
        val rows = (root["rows"] as? JsonArray) ?: JsonArray(emptyList())
        return VitacoraImportJob(
            id = job.str("id"),
            organizationId = job.str("organization_id"),
            organizationName = root.strOrNull("organization_name"),
            mode = runCatching { VitacoraImportMode.valueOf(job.str("mode")) }.getOrDefault(VitacoraImportMode.SELF_SERVICE),
            status = runCatching { VitacoraImportJobStatus.valueOf(job.str("status")) }.getOrDefault(VitacoraImportJobStatus.UPLOADED),
            templateVersion = job["template_version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
            initiatedBy = job.strOrNull("initiated_by").orEmpty(),
            executedBy = job.strOrNull("executed_by"),
            comment = job.strOrNull("comment"),
            storagePath = job.strOrNull("storage_path"),
            fileSha256 = job.strOrNull("file_sha256"),
            createdAtEpochMs = parseTs(job.strOrNull("created_at")),
            analyzedAtEpochMs = job.strOrNull("analyzed_at")?.let { parseTs(it) },
            confirmedAtEpochMs = job.strOrNull("confirmed_at")?.let { parseTs(it) },
            completedAtEpochMs = job.strOrNull("completed_at")?.let { parseTs(it) },
            summary = VitacoraImportSummary(
                total = job["total_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: rows.size,
                ready = job["valid_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                errors = job["failed_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                possibleDuplicates = job["duplicate_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                alreadyExists = job["existing_rows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                created = job["created_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            ),
            rows = rows.map { decodeRow(it.jsonObject) }
        )
    }

    private fun decodeRow(o: JsonObject): VitacoraImportRowAnalysis {
        return VitacoraImportRowAnalysis(
            rowNumber = o["row_number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            externalPetId = o.strOrNull("external_pet_id"),
            externalPetIdNormalized = o.strOrNull("external_pet_id_normalized"),
            name = o.strOrNull("pet_name"),
            status = runCatching { VitacoraImportRowStatus.valueOf(o.str("status")) }.getOrDefault(VitacoraImportRowStatus.ERROR),
            createdPetId = o.strOrNull("created_pet_id"),
            createdVitacoraNumber = o["created_vitacora_number"]?.jsonPrimitive?.longOrNull
        )
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.strOrNull(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun parseTs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
    }
}
