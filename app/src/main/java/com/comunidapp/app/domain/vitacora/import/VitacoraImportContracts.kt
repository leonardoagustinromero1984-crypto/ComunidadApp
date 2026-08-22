package com.comunidapp.app.domain.vitacora.import

import java.util.Locale

enum class VitacoraImportScope {
    ORGANIZATION,
    INDEPENDENT_RESCUER
}

object VitacoraImportRowClassifier {
    val ERROR_CODES = setOf(
        "REQUIRED", "ENUM_INVALID", "DATE_INVALID", "YES_NO_INVALID",
        "COUNTRY_INVALID", "ADMINISTRATIVE_AREA_INVALID", "LOCALITY_INVALID",
        "LOCALITY_NOT_IN_AREA", "FORMULA_FORBIDDEN", "AGE_INVALID"
    )
    val WARNING_CODES = setOf(
        "STATUS_ADOPTION_NOT_PUBLISHED",
        "AGE_BIRTH_MISMATCH"
    )

    fun status(
        issues: List<VitacoraImportFieldIssue>
    ): VitacoraImportRowStatus {
        val codes = issues.map { it.code }.toSet()
        return when {
            "ALREADY_EXISTS" in codes -> VitacoraImportRowStatus.YA_EXISTE
            "DUPLICATE_IN_FILE" in codes -> VitacoraImportRowStatus.POSIBLE_DUPLICADO
            codes.any { it in ERROR_CODES } -> VitacoraImportRowStatus.ERROR
            codes.any { it in WARNING_CODES } || issues.isNotEmpty() -> VitacoraImportRowStatus.ADVERTENCIA
            else -> VitacoraImportRowStatus.LISTA
        }
    }
}

/**
 * Single authoritative age parser shared with SQL `_import_parse_age_months`.
 * Analyze stores months; confirm persists that value without reinterpreting Excel text.
 */
object VitacoraImportAgeNormalization {
    fun parseMonths(raw: String?): Int? {
        val text = normalize(raw.orEmpty())
        if (text.isEmpty()) return null
        val years = Regex("(\\d+)\\s*(anios?|anos?|years?|a)\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val months = Regex("(\\d+)\\s*(meses?|months?|m)\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val bare = Regex("^(\\d+)$").matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()
        val value = when {
            years != null && months != null -> years * 12 + months
            years != null -> years * 12
            months != null -> months
            bare != null && bare <= 30 -> bare * 12
            bare != null && bare <= 480 -> bare
            else -> null
        }
        return value?.takeIf { it in 0..480 }
    }

    fun normalize(raw: String): String {
        val lower = raw.trim().lowercase(Locale("es", "AR"))
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
