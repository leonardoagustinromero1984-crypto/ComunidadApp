package com.comunidapp.app.domain.vitacora.import

import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.domain.i18n.CountryCatalog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class VitacoraImportRawRow(
    val rowNumber: Int,
    val values: Map<String, String>,
    val formulaFields: Set<String> = emptySet()
)

data class VitacoraImportWorkbookPayload(
    val templateType: String?,
    val templateVersion: Int?,
    val rows: List<VitacoraImportRawRow>,
    val hasVba: Boolean = false,
    val sheetCount: Int = 0,
    val fileSizeBytes: Long = 0,
    val rejected: String? = null
)

data class VitacoraImportAnalyzeContext(
    val organizationId: String,
    val existingNormalizedExternalIds: Set<String>,
    val locationNodes: List<LocationNode>,
    val verifiedOrganization: Boolean,
    val actorCanImport: Boolean,
    val mode: VitacoraImportMode
)

object VitacoraImportColumns {
    const val EXTERNAL_ID = "external_pet_id"
    const val NAME = "name"
    const val SPECIES = "species"
    const val SEX = "sex"
    const val STATUS = "status"
    const val COUNTRY = "country"
    const val ADMIN_AREA = "administrative_area"
    const val LOCALITY = "locality"
    const val AGE = "estimated_age"
    const val BIRTH = "birth_date"
    const val BREED = "breed"
    const val SIZE = "size"
    const val COLOR = "color"
    const val NEUTERED = "neutered"
    const val VACCINATED = "vaccinated"
    const val DESCRIPTION = "description"
    const val INTAKE = "intake_date"
    const val DOGS = "lives_with_dogs"
    const val CATS = "lives_with_cats"
    const val KIDS = "lives_with_kids"
    const val SPECIAL = "special_needs"
    const val NOTES = "notes"

    val REQUIRED = listOf(EXTERNAL_ID, NAME, SPECIES, SEX, STATUS, ADMIN_AREA, LOCALITY)

    val HEADERS = linkedMapOf(
        EXTERNAL_ID to "Referencia interna",
        NAME to "Nombre",
        SPECIES to "Especie",
        SEX to "Sexo",
        STATUS to "Estado",
        COUNTRY to "País",
        ADMIN_AREA to "Provincia",
        LOCALITY to "Localidad",
        AGE to "Edad aproximada",
        BIRTH to "Fecha de nacimiento",
        BREED to "Raza",
        SIZE to "Tamaño",
        COLOR to "Color",
        NEUTERED to "Castrado",
        VACCINATED to "Vacunado",
        DESCRIPTION to "Descripción",
        INTAKE to "Fecha de ingreso",
        DOGS to "Convive con perros",
        CATS to "Convive con gatos",
        KIDS to "Convive con niños",
        SPECIAL to "Necesidades especiales",
        NOTES to "Observaciones"
    )

    /** Downloadable template omits País; parser still accepts v1 files that include it. */
    val DOWNLOAD_HEADERS: Map<String, String> = HEADERS.filterKeys { it != COUNTRY }

    /** Declared extras that are not canonical pet columns. Color/description map to notes. */
    val UNMAPPED_PROFILE_FIELDS = setOf(DOGS, CATS, KIDS, SPECIAL)
}

object VitacoraImportAnalyzer {

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    )

    fun analyze(
        workbook: VitacoraImportWorkbookPayload,
        context: VitacoraImportAnalyzeContext
    ): Pair<VitacoraImportSummary, List<VitacoraImportRowAnalysis>> {
        if (!context.actorCanImport) {
            return errorJob("FORBIDDEN", "No tenés permiso para importar mascotas.")
        }
        if (context.mode != VitacoraImportMode.ADMIN && !context.verifiedOrganization) {
            return errorJob("ORG_NOT_VERIFIED", "La organización debe estar verificada para importar.")
        }
        workbook.rejected?.let { return errorJob(it, securityMessage(it)) }
        if (workbook.hasVba) return errorJob("XLSM_REJECTED", "No se admiten archivos con macros.")
        if (workbook.fileSizeBytes > VitacoraImportPolicy.MAX_FILE_SIZE_BYTES) {
            return errorJob("MAX_FILE_SIZE_EXCEEDED", "El archivo supera el tamaño máximo permitido.")
        }
        if (workbook.sheetCount > VitacoraImportPolicy.MAX_SHEETS) {
            return errorJob("TOO_MANY_SHEETS", "El libro tiene demasiadas hojas.")
        }
        if (workbook.templateType.isNullOrBlank()) {
            return errorJob("TEMPLATE_META_MISSING", "Falta la metadata de la plantilla LeoVer.")
        }
        if (workbook.templateType != VitacoraImportPolicy.TEMPLATE_TYPE) {
            return errorJob("TEMPLATE_TYPE_INVALID", "Esta plantilla no es de importación VitaCora.")
        }
        if (workbook.templateVersion != VitacoraImportPolicy.TEMPLATE_VERSION) {
            return errorJob("TEMPLATE_VERSION_INVALID", "Usá la plantilla v1 vigente.")
        }
        if (workbook.rows.size > VitacoraImportPolicy.MAX_ROWS) {
            return errorJob("MAX_ROWS_EXCEEDED", "El máximo es ${VitacoraImportPolicy.MAX_ROWS} mascotas por archivo.")
        }

        val seen = mutableMapOf<String, Int>()
        val analyzed = workbook.rows.map { row -> analyzeRow(row, context, seen) }
        val summary = summaryOf(analyzed)
        return summary to analyzed
    }

    fun summaryOf(rows: List<VitacoraImportRowAnalysis>): VitacoraImportSummary {
        fun count(status: VitacoraImportRowStatus) = rows.count { it.status == status }
        return VitacoraImportSummary(
            total = rows.size,
            ready = count(VitacoraImportRowStatus.LISTA),
            warnings = count(VitacoraImportRowStatus.ADVERTENCIA),
            errors = count(VitacoraImportRowStatus.ERROR),
            possibleDuplicates = count(VitacoraImportRowStatus.POSIBLE_DUPLICADO),
            alreadyExists = count(VitacoraImportRowStatus.YA_EXISTE)
        )
    }

    private fun errorJob(code: String, message: String): Pair<VitacoraImportSummary, List<VitacoraImportRowAnalysis>> {
        val row = VitacoraImportRowAnalysis(
            rowNumber = 0,
            externalPetId = null,
            externalPetIdNormalized = null,
            name = null,
            status = VitacoraImportRowStatus.ERROR,
            issues = listOf(
                VitacoraImportFieldIssue("archivo", code, message, "Revisá el archivo y volvé a intentar.")
            )
        )
        return VitacoraImportSummary(total = 0, errors = 1) to listOf(row)
    }

    private fun securityMessage(code: String) = when (code) {
        "XLS_REJECTED" -> "Solo se admite .xlsx. El formato .xls no es válido."
        "XLSM_REJECTED" -> "No se admiten macros ni .xlsm."
        "XLSX_CORRUPT" -> "El archivo está dañado o no es un XLSX válido."
        "FAKE_XLSX" -> "El archivo no es un XLSX válido."
        else -> "El archivo no superó la validación de seguridad."
    }

    private fun analyzeRow(
        row: VitacoraImportRawRow,
        context: VitacoraImportAnalyzeContext,
        seen: MutableMap<String, Int>
    ): VitacoraImportRowAnalysis {
        val issues = mutableListOf<VitacoraImportFieldIssue>()
        fun cell(key: String) = row.values[key]?.trim().orEmpty()

        row.formulaFields.forEach { field ->
            issues += issue(field, "FORMULA_FORBIDDEN", "La celda contiene una fórmula.", "Completá el valor, no una fórmula.")
        }

        val externalDisplay = cell(VitacoraImportColumns.EXTERNAL_ID)
        val externalNorm = ExternalPetIdNormalizer.normalize(externalDisplay)
        val name = cell(VitacoraImportColumns.NAME)
        required(externalDisplay, VitacoraImportColumns.EXTERNAL_ID, "Referencia interna", issues)
        required(name, VitacoraImportColumns.NAME, "Nombre", issues)

        val species = mapEnum(
            cell(VitacoraImportColumns.SPECIES),
            mapOf(
                "perro" to "DOG", "dog" to "DOG", "canino" to "DOG",
                "gato" to "CAT", "cat" to "CAT", "felino" to "CAT",
                "otro" to "OTHER", "otra" to "OTHER", "other" to "OTHER"
            ),
            VitacoraImportColumns.SPECIES,
            "Especie",
            issues,
            required = true
        )
        val sex = mapEnum(
            cell(VitacoraImportColumns.SEX),
            mapOf(
                "macho" to "MALE", "male" to "MALE", "m" to "MALE",
                "hembra" to "FEMALE", "female" to "FEMALE", "h" to "FEMALE", "f" to "FEMALE",
                "desconocido" to "UNKNOWN", "unknown" to "UNKNOWN"
            ),
            VitacoraImportColumns.SEX,
            "Sexo",
            issues,
            required = true
        )
        var lifecycle = "ACTIVE"
        val statusRaw = cell(VitacoraImportColumns.STATUS)
        when (norm(statusRaw)) {
            "" -> issues += issue(VitacoraImportColumns.STATUS, "REQUIRED", "El estado es obligatorio.", "Usá Activo o Archivado.")
            "activo", "active" -> lifecycle = "ACTIVE"
            "archivado", "archived" -> lifecycle = "ARCHIVED"
            "en adopcion", "en adopción", "adopcion", "adopción" -> {
                lifecycle = "ACTIVE"
                issues += issue(
                    VitacoraImportColumns.STATUS,
                    "STATUS_ADOPTION_NOT_PUBLISHED",
                    "En adopción no publica un aviso. La VitaCora se crea activa.",
                    "Publicá en adopción después, cuando esté lista."
                )
            }
            else -> issues += issue(VitacoraImportColumns.STATUS, "ENUM_INVALID", "Estado no reconocido.", "Usá Activo o Archivado.")
        }

        val countryRaw = cell(VitacoraImportColumns.COUNTRY)
            .ifBlank { com.comunidapp.app.domain.i18n.MarketUxPolicy.defaultCountryDisplayName() }
        val locations = resolveLocation(
            countryRaw,
            cell(VitacoraImportColumns.ADMIN_AREA),
            cell(VitacoraImportColumns.LOCALITY),
            context.locationNodes,
            issues
        )

        val ageMonths = parseAgeMonths(cell(VitacoraImportColumns.AGE), issues)
        val birthDate = parseDate(cell(VitacoraImportColumns.BIRTH), VitacoraImportColumns.BIRTH, issues)
        if (ageMonths != null && birthDate != null) {
            val approxYears = ageMonths / 12
            val birthYear = LocalDate.parse(birthDate).year
            val nowYear = LocalDate.now().year
            if (kotlin.math.abs((nowYear - birthYear) - approxYears) > 1) {
                issues += issue(
                    VitacoraImportColumns.AGE,
                    "AGE_BIRTH_MISMATCH",
                    "La edad aproximada no coincide con la fecha de nacimiento.",
                    "Dejá uno de los dos campos o corregí el valor."
                )
            }
        }
        val precision = when {
            birthDate != null -> "EXACT_DATE"
            ageMonths != null -> "ESTIMATED"
            else -> "UNKNOWN"
        }

        val size = mapEnum(
            cell(VitacoraImportColumns.SIZE),
            mapOf(
                "pequeno" to "SMALL", "pequeño" to "SMALL", "chico" to "SMALL", "small" to "SMALL",
                "mediano" to "MEDIUM", "medium" to "MEDIUM",
                "grande" to "LARGE", "large" to "LARGE"
            ),
            VitacoraImportColumns.SIZE,
            "Tamaño",
            issues,
            required = false
        )

        parseYesNo(cell(VitacoraImportColumns.NEUTERED), VitacoraImportColumns.NEUTERED, issues)
        parseYesNo(cell(VitacoraImportColumns.VACCINATED), VitacoraImportColumns.VACCINATED, issues)
        parseYesNo(cell(VitacoraImportColumns.DOGS), VitacoraImportColumns.DOGS, issues)
        parseYesNo(cell(VitacoraImportColumns.CATS), VitacoraImportColumns.CATS, issues)
        parseYesNo(cell(VitacoraImportColumns.KIDS), VitacoraImportColumns.KIDS, issues)

        val unmapped = mutableMapOf<String, String>()
        VitacoraImportColumns.UNMAPPED_PROFILE_FIELDS.forEach { key ->
            val value = cell(key)
            if (value.isNotEmpty()) unmapped[key] = value
        }
        if (cell(VitacoraImportColumns.NEUTERED).isNotEmpty()) {
            unmapped["neutered_declared"] = cell(VitacoraImportColumns.NEUTERED)
        }
        if (cell(VitacoraImportColumns.VACCINATED).isNotEmpty()) {
            unmapped["vaccinated_declared"] = cell(VitacoraImportColumns.VACCINATED)
        }

        val notesParts = listOfNotNull(
            cell(VitacoraImportColumns.NOTES).takeIf { it.isNotEmpty() },
            cell(VitacoraImportColumns.DESCRIPTION).takeIf { it.isNotEmpty() }?.let { "Descripción: $it" },
            cell(VitacoraImportColumns.COLOR).takeIf { it.isNotEmpty() }?.let { "Color: $it" },
            cell(VitacoraImportColumns.SPECIAL).takeIf { it.isNotEmpty() }?.let { "Necesidades especiales: $it" }
        )
        val notes = notesParts.joinToString("\n").ifBlank { null }
        val intake = parseDate(cell(VitacoraImportColumns.INTAKE), VitacoraImportColumns.INTAKE, issues)

        if (externalNorm != null) {
            val first = seen.putIfAbsent(externalNorm, row.rowNumber)
            if (first != null) {
                issues += issue(
                    VitacoraImportColumns.EXTERNAL_ID,
                    "DUPLICATE_IN_FILE",
                    "La referencia se repite (fila $first).",
                    "Usá una referencia interna única por mascota."
                )
            } else if (externalNorm in context.existingNormalizedExternalIds) {
                issues += issue(
                    VitacoraImportColumns.EXTERNAL_ID,
                    "ALREADY_EXISTS",
                    "Ya existe una mascota con esa referencia en la organización.",
                    "No se actualiza automáticamente."
                )
            }
        }

        val blocking = issues.filter {
            it.code in setOf(
                "REQUIRED", "ENUM_INVALID", "DATE_INVALID", "YES_NO_INVALID",
                "COUNTRY_INVALID", "ADMINISTRATIVE_AREA_INVALID", "LOCALITY_INVALID",
                "LOCALITY_NOT_IN_AREA", "FORMULA_FORBIDDEN", "AGE_INVALID"
            )
        }
        val duplicateFile = issues.any { it.code == "DUPLICATE_IN_FILE" }
        val exists = issues.any { it.code == "ALREADY_EXISTS" }
        val status = when {
            exists -> VitacoraImportRowStatus.YA_EXISTE
            duplicateFile -> VitacoraImportRowStatus.POSIBLE_DUPLICADO
            blocking.isNotEmpty() -> VitacoraImportRowStatus.ERROR
            issues.isNotEmpty() -> VitacoraImportRowStatus.ADVERTENCIA
            else -> VitacoraImportRowStatus.LISTA
        }

        val mapped = if (
            status == VitacoraImportRowStatus.LISTA || status == VitacoraImportRowStatus.ADVERTENCIA
        ) {
            MappedVitacoraImportRow(
                externalPetIdDisplay = externalDisplay,
                externalPetIdNormalized = externalNorm.orEmpty(),
                name = name,
                speciesCode = species ?: "DOG",
                sex = sex ?: "UNKNOWN",
                lifecycleStatus = lifecycle,
                countryId = locations.countryId,
                administrativeAreaId = locations.adminId,
                localityId = locations.localityId,
                estimatedAgeMonths = ageMonths,
                birthDate = birthDate,
                birthPrecision = precision,
                breedName = cell(VitacoraImportColumns.BREED).takeIf { it.isNotEmpty() },
                size = size,
                notes = notes,
                intakeDate = intake,
                unmapped = unmapped
            )
        } else null

        return VitacoraImportRowAnalysis(
            rowNumber = row.rowNumber,
            externalPetId = externalDisplay.ifBlank { null },
            externalPetIdNormalized = externalNorm,
            name = name.ifBlank { null },
            status = status,
            issues = issues,
            mapped = mapped
        )
    }

    private data class ResolvedLocation(
        val countryId: String?,
        val adminId: String?,
        val localityId: String?
    )

    private fun resolveLocation(
        countryRaw: String,
        adminRaw: String,
        localityRaw: String,
        nodes: List<LocationNode>,
        issues: MutableList<VitacoraImportFieldIssue>
    ): ResolvedLocation {
        val country = matchNode(countryRaw, nodes.filter { it.level == LocationLevel.COUNTRY })
        if (country == null) {
            issues += issue(
                VitacoraImportColumns.COUNTRY,
                "COUNTRY_INVALID",
                "No encontramos el país \"$countryRaw\".",
                "En v1 usá Argentina."
            )
        } else if (!CountryCatalog.canSelectForOnboarding(country.code ?: CountryCatalog.INITIAL_COUNTRY_ISO) &&
            !country.code.equals("AR", true) &&
            norm(country.name) != "argentina"
        ) {
            issues += issue(
                VitacoraImportColumns.COUNTRY,
                "COUNTRY_INVALID",
                "Ese país todavía no está habilitado.",
                "Usá Argentina."
            )
        }
        required(adminRaw, VitacoraImportColumns.ADMIN_AREA, "Provincia", issues)
        required(localityRaw, VitacoraImportColumns.LOCALITY, "Localidad", issues)
        val admin = matchNode(
            adminRaw,
            nodes.filter { it.level == LocationLevel.PROVINCE && (country == null || it.parentId == country.id) }
        )
        if (adminRaw.isNotBlank() && admin == null) {
            issues += issue(
                VitacoraImportColumns.ADMIN_AREA,
                "ADMINISTRATIVE_AREA_INVALID",
                "No encontramos \"$adminRaw\".",
                "Revisá la provincia seleccionada."
            )
        }
        val locality = matchNode(
            localityRaw,
            nodes.filter { it.level == LocationLevel.LOCALITY }
        )
        if (localityRaw.isNotBlank() && locality == null) {
            issues += issue(
                VitacoraImportColumns.LOCALITY,
                "LOCALITY_INVALID",
                "No encontramos \"$localityRaw\".",
                "Revisá la localidad seleccionada."
            )
        } else if (locality != null && admin != null && !parentWalk(nodes, locality.id, admin.id)) {
            issues += issue(
                VitacoraImportColumns.LOCALITY,
                "LOCALITY_NOT_IN_AREA",
                "La localidad no pertenece a esa provincia.",
                "Elegí una localidad de la provincia indicada."
            )
        }
        return ResolvedLocation(country?.id, admin?.id, locality?.id)
    }

    private fun parentWalk(nodes: List<LocationNode>, startId: String, targetParent: String?): Boolean {
        if (targetParent == null) return true
        val map = nodes.associateBy { it.id }
        var current = map[startId]
        var hops = 0
        while (current != null && hops < 8) {
            if (current.id == targetParent || current.parentId == targetParent) return true
            current = current.parentId?.let { map[it] }
            hops++
        }
        return false
    }

    private fun matchNode(raw: String, candidates: List<LocationNode>): LocationNode? {
        val n = norm(raw)
        if (n.isEmpty()) return null
        return candidates.firstOrNull { norm(it.name) == n }
            ?: candidates.firstOrNull { it.aliases.any { alias -> norm(alias) == n } }
            ?: candidates.firstOrNull { it.code?.equals(raw.trim(), true) == true }
    }

    private fun parseAgeMonths(raw: String, issues: MutableList<VitacoraImportFieldIssue>): Int? {
        if (raw.isBlank()) return null
        val n = norm(raw)
        val months = Regex("(\\d+)\\s*m").find(n)?.groupValues?.get(1)?.toIntOrNull()
        val years = Regex("(\\d+)\\s*(a|anios|años|y)").find(n)?.groupValues?.get(1)?.toIntOrNull()
        val bare = n.toIntOrNull()
        val value = when {
            months != null && years != null -> years * 12 + months
            months != null -> months
            years != null -> years * 12
            bare != null && bare <= 480 -> if (bare <= 36 && n.contains("mes")) bare else if (bare <= 30) bare * 12 else bare
            bare != null -> bare * 12
            else -> null
        }
        if (value == null) {
            issues += issue(VitacoraImportColumns.AGE, "AGE_INVALID", "Edad aproximada inválida.", "Usá años o meses (ej. 2 años).")
        }
        return value
    }

    private fun parseDate(raw: String, field: String, issues: MutableList<VitacoraImportFieldIssue>): String? {
        if (raw.isBlank()) return null
        for (fmt in dateFormats) {
            try {
                return LocalDate.parse(raw.trim(), fmt).toString()
            } catch (_: DateTimeParseException) {
            }
        }
        issues += issue(field, "DATE_INVALID", "Fecha inválida.", "Usá AAAA-MM-DD o DD/MM/AAAA.")
        return null
    }

    private fun parseYesNo(raw: String, field: String, issues: MutableList<VitacoraImportFieldIssue>): Boolean? {
        if (raw.isBlank()) return null
        return when (norm(raw)) {
            "si", "sí", "yes", "true", "1" -> true
            "no", "false", "0" -> false
            else -> {
                issues += issue(field, "YES_NO_INVALID", "Usá Sí o No.", "Completá con Sí o No.")
                null
            }
        }
    }

    private fun mapEnum(
        raw: String,
        aliases: Map<String, String>,
        field: String,
        label: String,
        issues: MutableList<VitacoraImportFieldIssue>,
        required: Boolean
    ): String? {
        if (raw.isBlank()) {
            if (required) required(raw, field, label, issues)
            return null
        }
        val mapped = aliases[norm(raw)]
        if (mapped == null) {
            issues += issue(field, "ENUM_INVALID", "$label no reconocido.", "Usá un valor de la lista de la plantilla.")
        }
        return mapped
    }

    private fun required(
        value: String,
        field: String,
        label: String,
        issues: MutableList<VitacoraImportFieldIssue>
    ) {
        if (value.isBlank()) {
            issues += issue(field, "REQUIRED", "$label es obligatorio.", "Completá $label.")
        }
    }

    private fun issue(field: String, code: String, message: String, suggestion: String) =
        VitacoraImportFieldIssue(field, code, message, suggestion)

    private fun norm(value: String): String {
        val lower = value.trim().lowercase(Locale("es", "AR"))
        val map = mapOf(
            'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a',
            'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
            'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
            'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
            'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
            'ñ' to 'n'
        )
        return buildString(lower.length) {
            lower.forEach { ch -> append(map[ch] ?: ch) }
        }
    }
}
