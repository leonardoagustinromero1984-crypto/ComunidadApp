package com.comunidapp.app.ui.components.leo

/**
 * FORM REQUIRED FIELD RULE — every obligatory field shows label + " *".
 * Optional fields stay unmarked.
 */
object LeoRequiredField {
    const val MARK = " *"

    fun label(text: String, required: Boolean = true): String {
        val trimmed = text.trimEnd()
        if (!required || trimmed.endsWith("*")) return trimmed
        return trimmed + MARK
    }
}
