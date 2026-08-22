package com.comunidapp.app.domain.legal

/**
 * Legal / consent consumer contracts. Documents are DRAFT_PRE_LAUNCH.
 * Tutorial state is not consent. No marketing checkbox. No automatic erasure cascade.
 */
enum class LegalDocumentStatus {
    DRAFT_PRE_LAUNCH,
    DRAFT,
    EFFECTIVE,
    SUPERSEDED
}

enum class ConsentEventType {
    ACCEPTED,
    WITHDRAWN,
    RECORDED
}

data class LegalDocumentRef(
    val id: String,
    val kind: String,
    val version: String,
    val status: LegalDocumentStatus = LegalDocumentStatus.DRAFT_PRE_LAUNCH
)

data class ConsentEventDraft(
    val subjectUserId: String,
    val documentId: String,
    val eventType: ConsentEventType,
    val source: String = "APP"
)

data class PrivacyRequestDraft(
    val type: String
)

data class TutorialState(
    val tutorialKey: String,
    val version: Int,
    val viewed: Boolean = false,
    val skipped: Boolean = false,
    val completed: Boolean = false
) {
    init {
        require(tutorialKey.isNotBlank())
    }
}

object LegalConsentRules {
    const val CURRENT_STATUS = "DRAFT_PRE_LAUNCH"
    fun marketingConsentAllowed(): Boolean = false
    fun tutorialIsConsent(): Boolean = false
    fun automaticErasureCascade(): Boolean = false
    fun allTutorialsSkippable(): Boolean = true
    fun allTutorialsReopenable(): Boolean = true
}
