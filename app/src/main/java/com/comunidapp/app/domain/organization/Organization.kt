package com.comunidapp.app.domain.organization

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind

@JvmInline
value class OrganizationId(val value: String) {
    init {
        require(value.isNotBlank()) { "organization id blank" }
    }
}

@JvmInline
value class OrganizationSlug private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun ofNormalized(normalized: String): OrganizationSlug = OrganizationSlug(normalized)
    }
}

enum class OrganizationType {
    SHELTER,
    RESCUE_GROUP,
    NGO,
    VETERINARY_CLINIC,
    PET_SHOP,
    TRAINING_CENTER,
    WALKER_AGENCY,
    OTHER
}

enum class OrganizationStatus {
    DRAFT,
    ACTIVE,
    RESTRICTED,
    SUSPENDED,
    CLOSED,
    REJECTED
}

enum class OrganizationVerificationStatus {
    NOT_REQUESTED,
    PENDING,
    VERIFIED,
    REJECTED,
    EXPIRED
}

/**
 * Contacto institucional separado de Auth / UserProfile (D-M03-06).
 */
data class OrganizationContactVisibility(
    val showEmail: Boolean = false,
    val showPhone: Boolean = false
)

data class Organization(
    val id: OrganizationId,
    val legalName: String,
    val publicName: String,
    val slug: OrganizationSlug,
    val type: OrganizationType,
    val typeDescription: String? = null,
    val description: String? = null,
    val status: OrganizationStatus = OrganizationStatus.DRAFT,
    val verificationStatus: OrganizationVerificationStatus =
        OrganizationVerificationStatus.NOT_REQUESTED,
    val institutionalEmail: String? = null,
    val institutionalPhone: String? = null,
    val contactVisibility: OrganizationContactVisibility = OrganizationContactVisibility(),
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
    val createdByUserId: String,
    val createdAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long? = null
)

/**
 * Proyección pública allowlist. Sin email/phone salvo opt-in; sin miembros/roles.
 */
data class PublicOrganization(
    val id: String,
    val publicName: String,
    val slug: String,
    val type: OrganizationType,
    val description: String? = null,
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
    val status: OrganizationStatus,
    val verificationStatus: OrganizationVerificationStatus,
    val publicEmail: String? = null,
    val publicPhone: String? = null
)

data class OrganizationBranch(
    val id: String,
    val organizationId: OrganizationId,
    val name: String,
    val address: String? = null,
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
    val phone: String? = null,
    val scheduleText: String? = null,
    val active: Boolean = true
)

enum class OrganizationValidationErrorCode {
    NAME_EMPTY,
    NAME_TOO_SHORT,
    NAME_TOO_LONG,
    TYPE_OTHER_NEEDS_DESCRIPTION,
    SLUG_INVALID,
    DESCRIPTION_TOO_LONG,
    COUNTRY_CODE_INVALID,
    CANNOT_SELF_VERIFY
}

class OrganizationValidationException(
    val error: AppError
) : Exception(error.technicalMessage)

object OrganizationValidators {

    const val NAME_MIN = 2
    const val NAME_MAX = 120
    const val DESCRIPTION_MAX = 2000

    fun validateCreate(
        legalName: String,
        publicName: String,
        type: OrganizationType,
        typeDescription: String?,
        slugRaw: String,
        description: String? = null,
        countryCode: String? = null
    ): Result<ValidatedOrganizationDraft> {
        val legal = legalName.trim()
        val public = publicName.trim()
        if (legal.length < NAME_MIN) {
            return failure(OrganizationValidationErrorCode.NAME_TOO_SHORT, "legal name short")
        }
        if (legal.length > NAME_MAX) {
            return failure(OrganizationValidationErrorCode.NAME_TOO_LONG, "legal name long")
        }
        if (public.length < NAME_MIN) {
            return failure(OrganizationValidationErrorCode.NAME_TOO_SHORT, "public name short")
        }
        if (public.length > NAME_MAX) {
            return failure(OrganizationValidationErrorCode.NAME_TOO_LONG, "public name long")
        }
        if (type == OrganizationType.OTHER && typeDescription.isNullOrBlank()) {
            return failure(
                OrganizationValidationErrorCode.TYPE_OTHER_NEEDS_DESCRIPTION,
                "OTHER needs description"
            )
        }
        val slug = OrganizationSlugValidators.validate(slugRaw).getOrElse {
            return Result.failure(it)
        }
        val desc = description?.trim()?.ifBlank { null }
        if (desc != null && desc.length > DESCRIPTION_MAX) {
            return failure(OrganizationValidationErrorCode.DESCRIPTION_TOO_LONG, "desc long")
        }
        val cc = countryCode?.trim()?.uppercase()?.ifBlank { null }
        if (cc != null && !Regex("^[A-Z]{2}$").matches(cc)) {
            return failure(OrganizationValidationErrorCode.COUNTRY_CODE_INVALID, "country")
        }
        return Result.success(
            ValidatedOrganizationDraft(
                legalName = legal,
                publicName = public,
                type = type,
                typeDescription = typeDescription?.trim()?.ifBlank { null },
                slug = slug,
                description = desc,
                countryCode = cc
            )
        )
    }

    fun toPublic(organization: Organization): PublicOrganization {
        val showEmail = organization.contactVisibility.showEmail
        val showPhone = organization.contactVisibility.showPhone
        return PublicOrganization(
            id = organization.id.value,
            publicName = organization.publicName,
            slug = organization.slug.value,
            type = organization.type,
            description = organization.description,
            city = organization.city,
            province = organization.province,
            countryCode = organization.countryCode,
            status = organization.status,
            verificationStatus = organization.verificationStatus,
            publicEmail = if (showEmail) organization.institutionalEmail else null,
            publicPhone = if (showPhone) organization.institutionalPhone else null
        )
    }

    /** Auto-verificación prohibida (D-M03-05). */
    fun assertNotSelfVerification(actorUserId: String, reviewerUserId: String): Result<Unit> {
        if (actorUserId == reviewerUserId) {
            return failure(
                OrganizationValidationErrorCode.CANNOT_SELF_VERIFY,
                "self verification"
            )
        }
        return Result.success(Unit)
    }

    private fun failure(code: OrganizationValidationErrorCode, technical: String): Result<Nothing> =
        Result.failure(
            OrganizationValidationException(
                AppError(
                    kind = AppErrorKind.VALIDATION,
                    userMessage = userMessage(code),
                    technicalMessage = technical,
                    code = code.name
                )
            )
        )

    private fun userMessage(code: OrganizationValidationErrorCode): String = when (code) {
        OrganizationValidationErrorCode.NAME_EMPTY -> "Ingresá un nombre."
        OrganizationValidationErrorCode.NAME_TOO_SHORT -> "El nombre es demasiado corto."
        OrganizationValidationErrorCode.NAME_TOO_LONG -> "El nombre es demasiado largo."
        OrganizationValidationErrorCode.TYPE_OTHER_NEEDS_DESCRIPTION ->
            "Para el tipo Otro, describí la actividad."
        OrganizationValidationErrorCode.SLUG_INVALID -> "El identificador público no es válido."
        OrganizationValidationErrorCode.DESCRIPTION_TOO_LONG -> "La descripción es demasiado larga."
        OrganizationValidationErrorCode.COUNTRY_CODE_INVALID -> "Código de país inválido."
        OrganizationValidationErrorCode.CANNOT_SELF_VERIFY ->
            "Una organización no puede auto-verificarse."
    }
}

data class ValidatedOrganizationDraft(
    val legalName: String,
    val publicName: String,
    val type: OrganizationType,
    val typeDescription: String?,
    val slug: OrganizationSlug,
    val description: String?,
    val countryCode: String?
)
