package com.comunidapp.app.domain.validation

data class MissingRequirement(
    val id: String,
    val label: String,
    val sectionId: String? = null
)

data class ValidationSummary(
    val title: String = "Te falta completar:",
    val items: List<MissingRequirement>
) {
    val isEmpty: Boolean get() = items.isEmpty()
    fun message(): String =
        if (items.isEmpty()) "" else title + "\n" + items.joinToString("\n") { "• ${it.label}" }
}

object MissingRequirements {
    fun of(vararg pairs: Pair<Boolean, MissingRequirement>): ValidationSummary =
        ValidationSummary(items = pairs.filter { it.first }.map { it.second })

    fun message(summary: ValidationSummary): String = summary.message()
}
