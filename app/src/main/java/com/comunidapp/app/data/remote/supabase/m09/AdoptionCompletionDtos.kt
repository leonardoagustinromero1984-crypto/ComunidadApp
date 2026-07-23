package com.comunidapp.app.data.remote.supabase.m09

import com.comunidapp.app.data.model.AdoptionAgreement
import com.comunidapp.app.data.model.AdoptionAgreementStatus
import com.comunidapp.app.data.model.AdoptionDocumentRequirement
import com.comunidapp.app.data.model.AdoptionDocumentStatus
import com.comunidapp.app.data.model.AdoptionDocumentType
import com.comunidapp.app.data.model.AdoptionFollowUpCheck
import com.comunidapp.app.data.model.AdoptionFollowUpPlan
import com.comunidapp.app.data.model.AdoptionFollowUpPlanStatus
import com.comunidapp.app.data.model.AdoptionFollowUpStatus
import com.comunidapp.app.data.model.AdoptionInterview
import com.comunidapp.app.data.model.AdoptionInterviewStatus
import com.comunidapp.app.data.model.AdoptionInterviewType
import com.comunidapp.app.data.model.AdoptionWelfareStatus
import com.comunidapp.app.data.model.FinalizedAdoption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AdoptionInterviewRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("application_id") val applicationId: String,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("interview_type") val interviewType: String = "IN_PERSON",
    @SerialName("location_or_link") val locationOrLink: String? = null,
    val notes: String? = null,
    val status: String = "SCHEDULED",
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    val outcome: String? = null
)

@Serializable
data class AdoptionDocumentRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("application_id") val applicationId: String,
    @SerialName("doc_type") val docType: String = "OTHER",
    val required: Boolean = true,
    val status: String = "PENDING",
    @SerialName("storage_path") val storagePath: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null
)

@Serializable
data class AdoptionAgreementRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("application_id") val applicationId: String,
    @SerialName("adopter_user_id") val adopterUserId: String,
    @SerialName("publisher_user_id") val publisherUserId: String? = null,
    @SerialName("publisher_organization_id") val publisherOrganizationId: String? = null,
    @SerialName("terms_version") val termsVersion: String = "1.0",
    @SerialName("terms_snapshot") val termsSnapshot: String = "",
    @SerialName("adopter_accepted_at") val adopterAcceptedAt: String? = null,
    @SerialName("publisher_accepted_at") val publisherAcceptedAt: String? = null,
    val status: String = "DRAFT"
)

@Serializable
data class AdoptionFinalizationRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("application_id") val applicationId: String,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("adopter_user_id") val adopterUserId: String,
    @SerialName("finalized_at") val finalizedAt: String? = null,
    @SerialName("finalized_by") val finalizedBy: String = "",
    @SerialName("follow_up_plan_id") val followUpPlanId: String? = null
)

@Serializable
data class AdoptionFollowUpPlanRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("adopter_user_id") val adopterUserId: String,
    val status: String = "ACTIVE",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
)

@Serializable
data class AdoptionFollowUpCheckRow(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("adoption_id") val adoptionId: String = "",
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val status: String = "PENDING",
    val notes: String? = null,
    @SerialName("welfare_status") val welfareStatus: String? = null,
    @SerialName("evidence_ref") val evidenceRef: String? = null
)

private fun String?.toEpoch(): Long =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

private fun String?.toEpochOrNull(): Long? =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

fun AdoptionInterviewRow.toDomain() = AdoptionInterview(
    id = id,
    adoptionId = adoptionId,
    applicationId = applicationId,
    scheduledAt = scheduledAt.toEpoch(),
    type = AdoptionInterviewType.fromString(interviewType),
    locationOrLink = locationOrLink,
    notes = notes,
    status = AdoptionInterviewStatus.fromString(status),
    createdBy = createdBy,
    completedAt = completedAt.toEpochOrNull(),
    outcome = outcome
)

fun AdoptionDocumentRow.toDomain() = AdoptionDocumentRequirement(
    id = id,
    adoptionId = adoptionId,
    applicationId = applicationId,
    type = AdoptionDocumentType.fromString(docType),
    required = required,
    status = AdoptionDocumentStatus.fromString(status),
    storagePath = storagePath,
    rejectionReason = rejectionReason,
    submittedAt = submittedAt.toEpochOrNull(),
    reviewedAt = reviewedAt.toEpochOrNull()
)

fun AdoptionAgreementRow.toDomain() = AdoptionAgreement(
    id = id,
    adoptionId = adoptionId,
    applicationId = applicationId,
    adopterUserId = adopterUserId,
    publisherUserId = publisherUserId,
    publisherOrganizationId = publisherOrganizationId,
    termsVersion = termsVersion,
    termsSnapshot = termsSnapshot,
    adopterAcceptedAt = adopterAcceptedAt.toEpochOrNull(),
    publisherAcceptedAt = publisherAcceptedAt.toEpochOrNull(),
    status = AdoptionAgreementStatus.fromString(status)
)

fun AdoptionFinalizationRow.toDomain() = FinalizedAdoption(
    id = id,
    adoptionId = adoptionId,
    applicationId = applicationId,
    petId = petId,
    adopterUserId = adopterUserId,
    finalizedAt = finalizedAt.toEpoch(),
    finalizedBy = finalizedBy,
    followUpPlanId = followUpPlanId
)

fun AdoptionFollowUpPlanRow.toDomain() = AdoptionFollowUpPlan(
    id = id,
    adoptionId = adoptionId,
    adopterUserId = adopterUserId,
    status = AdoptionFollowUpPlanStatus.fromString(status),
    createdAt = createdAt.toEpoch(),
    completedAt = completedAt.toEpochOrNull()
)

fun AdoptionFollowUpCheckRow.toDomain() = AdoptionFollowUpCheck(
    id = id,
    planId = planId,
    adoptionId = adoptionId,
    dueAt = dueAt.toEpoch(),
    completedAt = completedAt.toEpochOrNull(),
    status = AdoptionFollowUpStatus.fromString(status),
    notes = notes,
    welfareStatus = welfareStatus?.let { AdoptionWelfareStatus.fromString(it) },
    evidenceRef = evidenceRef
).withOverdueDetection()
