package com.comunidapp.app.domain.vitacora.import

/**
 * Single import engine shared by SELF_SERVICE, ASSISTED and ADMIN.
 * Android may pre-validate UX; the server is the authority.
 */
object VitacoraImportPolicy {
    const val TEMPLATE_TYPE = "LEOVER_VITACORA_IMPORT"
    const val TEMPLATE_VERSION = 1
    const val MAX_ROWS = 500
    const val MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L
    const val MAX_STRING_CHARS = 2_000
    const val MAX_SHEETS = 12
    const val PUBLISHED_FILENAME = "LeoVer-Plantilla-Importacion-VitaCora-v1.xlsx"
    const val ASSET_PATH = "vitacora_import/LeoVer-Plantilla-Importacion-VitaCora-v1.xlsx"
    const val ORG_IMPORT_PERMISSION = "org.pets.import"
    const val ORG_PETS_MANAGE_PERMISSION = "org.pets.manage"
    const val EXISTING_PETS_AUTO_UPDATED = false
    const val NUMBER_GAPLESS_REQUIRED = false
    const val NUMBER_REUSE_ALLOWED = false
    const val NO_AUTO_ADOPTION_PUBLICATION = true
    const val NO_FAKE_MEDICAL_EVENTS = true
}

enum class VitacoraImportMode {
    SELF_SERVICE,
    ASSISTED,
    ADMIN
}

enum class VitacoraImportJobStatus {
    UPLOADED,
    ANALYZING,
    PENDING,
    EN_REVISION,
    READY,
    IMPORTING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    REQUIRES_CORRECTION,
    CANCELLED,
    FAILED
}

enum class VitacoraImportRowStatus {
    LISTA,
    ADVERTENCIA,
    ERROR,
    POSIBLE_DUPLICADO,
    YA_EXISTE
}

data class VitacoraImportFieldIssue(
    val field: String,
    val code: String,
    val message: String,
    val suggestion: String
)

data class VitacoraImportRowAnalysis(
    val rowNumber: Int,
    val externalPetId: String?,
    val externalPetIdNormalized: String?,
    val name: String?,
    val status: VitacoraImportRowStatus,
    val issues: List<VitacoraImportFieldIssue> = emptyList(),
    val mapped: MappedVitacoraImportRow? = null,
    val createdPetId: String? = null,
    val createdVitacoraNumber: Long? = null
) {
    val importable: Boolean
        get() = status == VitacoraImportRowStatus.LISTA ||
            status == VitacoraImportRowStatus.ADVERTENCIA
}

data class MappedVitacoraImportRow(
    val externalPetIdDisplay: String,
    val externalPetIdNormalized: String,
    val name: String,
    val speciesCode: String,
    val sex: String,
    val lifecycleStatus: String,
    val countryId: String?,
    val administrativeAreaId: String?,
    val localityId: String?,
    val estimatedAgeMonths: Int?,
    val birthDate: String?,
    val birthPrecision: String,
    val breedName: String?,
    val size: String?,
    val notes: String?,
    val intakeDate: String?,
    val unmapped: Map<String, String>
)

data class VitacoraImportSummary(
    val total: Int = 0,
    val ready: Int = 0,
    val warnings: Int = 0,
    val errors: Int = 0,
    val possibleDuplicates: Int = 0,
    val alreadyExists: Int = 0,
    val created: Int = 0
)

data class VitacoraImportJob(
    val id: String,
    val organizationId: String,
    val organizationName: String? = null,
    val importScope: VitacoraImportScope = VitacoraImportScope.ORGANIZATION,
    val rescuerPersonId: String? = null,
    val mode: VitacoraImportMode,
    val status: VitacoraImportJobStatus,
    val templateVersion: Int = VitacoraImportPolicy.TEMPLATE_VERSION,
    val initiatedBy: String,
    val executedBy: String? = null,
    val comment: String? = null,
    val storagePath: String? = null,
    val fileSha256: String? = null,
    val createdAtEpochMs: Long,
    val analyzedAtEpochMs: Long? = null,
    val confirmedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
    val summary: VitacoraImportSummary = VitacoraImportSummary(),
    val rows: List<VitacoraImportRowAnalysis> = emptyList()
)

data class VitacoraImportOrgHit(
    val id: String,
    val name: String,
    val slug: String,
    val verificationStatus: String,
    val kind: VitacoraImportScope = VitacoraImportScope.ORGANIZATION
)

object VitacoraNumberQuery {
    private val hashOrBare = Regex("^#?(\\d{1,18})$")
    private val labeled = Regex("(?i)^vitacora\\s*#?\\s*(\\d{1,18})$")

    fun parse(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        hashOrBare.matchEntire(text)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        labeled.matchEntire(text)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        return null
    }

    fun display(number: Long?): String? =
        number?.let { "VitaCora #$it" }

    fun petTitle(name: String, number: Long?): String =
        if (number == null) name else "$name · VitaCora #$number"
}

object ExternalPetIdNormalizer {
    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.lowercase() ?: return null
        if (value.isEmpty()) return null
        return value
    }
}

object VitacoraImportCopy {
    const val TITLE = "Importar mascotas"
    const val DOWNLOAD = "Descargar plantilla Excel"
    const val HOW_TO = "Ver cómo completar la plantilla"
    const val SELECT_FILE = "Seleccionar archivo"
    const val ASSISTED_TITLE = "¿Preferís que te ayudemos?"
    const val ASSISTED_BODY =
        "Podés completar la misma plantilla y enviárnosla. El equipo de LeoVer puede realizar la carga inicial por vos."
    const val ASSISTED_CTA = "Solicitar carga asistida"
    const val CREATE_N = "Crear %d VitaCoras"
    const val COMPLETED = "Importación completada"
    const val NEEDS_PHOTO = "Necesita foto"
    const val ORG_REF_LABEL = "Referencia de la organización"
}
