package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17CampaignType
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17InKindCategory
import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17VolunteerApplicationStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityType
import java.time.Instant
import java.time.ZoneId

data class MyMoneyContribution(
    val campaignTitle: String,
    val organizationName: String,
    val amountMinor: Long,
    val currency: String,
    val status: M17ContributionStatus,
    val createdAt: Long
)

data class MyGoodsPledge(
    val needTitle: String,
    val organizationName: String,
    val quantity: Int,
    val unit: String,
    val status: M17InKindPledgeStatus,
    val createdAt: Long
)

data class MyVolunteerInterest(
    val opportunityTitle: String,
    val organizationName: String,
    val status: M17VolunteerApplicationStatus,
    val createdAt: Long
)

object CommunityHelpPresentation {
    const val DISCOVER_TITLE = "Ayudar"
    const val DISCOVER_QUESTION = "¿Qué tipo de ayuda querés ofrecer?"
    const val ACTIVITY_TITLE = "Mi ayuda"
    const val MONEY_ACTION = "Aportar dinero"
    const val MONEY_HINT = "Campañas económicas activas."
    const val GOODS_ACTION = "Donar cosas"
    const val GOODS_HINT = "Necesidades materiales de organizaciones."
    const val TIME_ACTION = "Ofrecer mi tiempo"
    const val TIME_HINT = "Voluntariado, habilidades y traslados."
    const val WANT_TO_HELP = "Quiero ayudar"
    const val SECTION_MONEY = "Aportes económicos"
    const val SECTION_GOODS = "Bienes"
    const val SECTION_TIME = "Voluntariado"
    const val EMPTY_MONEY = "Todavía no declaraste aportes."
    const val EMPTY_GOODS = "Todavía no ofreciste bienes."
    const val EMPTY_TIME = "Todavía no ofreciste tu tiempo."
    const val PLEDGE_SAVED = "Registramos tu oferta. Quedó pendiente de confirmación."
    const val INTEREST_SAVED = "Registramos tu interés. La organización lo va a revisar."

    fun campaignType(type: M17CampaignType): String = when (type) {
        M17CampaignType.MEDICAL -> "Salud"
        M17CampaignType.FOOD_AND_SUPPLIES -> "Alimento e insumos"
        M17CampaignType.RESCUE -> "Rescate"
        M17CampaignType.SHELTER_INFRASTRUCTURE -> "Infraestructura"
        M17CampaignType.TRANSPORT -> "Traslados"
        M17CampaignType.EMERGENCY -> "Emergencia"
        M17CampaignType.GENERAL_SUPPORT -> "Apoyo general"
    }

    fun campaignStatus(status: M17CampaignStatus): String = when (status) {
        M17CampaignStatus.DRAFT -> "Borrador"
        M17CampaignStatus.PUBLISHED -> "Activa"
        M17CampaignStatus.PAUSED -> "Pausada"
        M17CampaignStatus.COMPLETED -> "Finalizada"
        M17CampaignStatus.CANCELLED -> "Cancelada"
    }

    fun contributionStatus(status: M17ContributionStatus): String = when (status) {
        M17ContributionStatus.PENDING -> "Pendiente de confirmación"
        M17ContributionStatus.CONFIRMED -> "Confirmado"
        M17ContributionStatus.REJECTED -> "No confirmado"
        M17ContributionStatus.FAILED -> "No se pudo registrar"
        M17ContributionStatus.CANCELLED -> "Cancelado"
        M17ContributionStatus.REFUNDED -> "Reembolsado"
    }

    fun goodsCategory(category: M17InKindCategory): String = when (category) {
        M17InKindCategory.FOOD -> "Alimento"
        M17InKindCategory.MEDICATION -> "Medicación"
        M17InKindCategory.HYGIENE -> "Higiene"
        M17InKindCategory.BEDDING -> "Abrigo"
        M17InKindCategory.TRANSPORT_SUPPLIES -> "Transporte"
        M17InKindCategory.CONSTRUCTION_MATERIALS -> "Materiales"
        M17InKindCategory.OTHER -> "Otra necesidad"
    }

    fun needStatus(status: M17InKindNeedStatus): String = when (status) {
        M17InKindNeedStatus.DRAFT -> "Borrador"
        M17InKindNeedStatus.PUBLISHED -> "Vigente"
        M17InKindNeedStatus.FULFILLED -> "Cubierta"
        M17InKindNeedStatus.CANCELLED -> "Cancelada"
    }

    fun pledgeStatus(status: M17InKindPledgeStatus): String = when (status) {
        M17InKindPledgeStatus.PLEDGED -> "Oferta registrada"
        M17InKindPledgeStatus.ACCEPTED -> "Aceptada"
        M17InKindPledgeStatus.DELIVERED -> "Entregada"
        M17InKindPledgeStatus.CANCELLED -> "Cancelada"
        M17InKindPledgeStatus.REJECTED -> "No aceptada"
    }

    fun volunteerType(type: M17VolunteerOpportunityType): String = when (type) {
        M17VolunteerOpportunityType.SHELTER_SUPPORT -> "Apoyo en el refugio"
        M17VolunteerOpportunityType.ANIMAL_CARE -> "Cuidado de animales"
        M17VolunteerOpportunityType.TRANSPORT -> "Traslados"
        M17VolunteerOpportunityType.EVENTS -> "Eventos"
        M17VolunteerOpportunityType.FUNDRAISING -> "Campañas"
        M17VolunteerOpportunityType.PHOTOGRAPHY -> "Fotografía"
        M17VolunteerOpportunityType.ADMINISTRATIVE -> "Tareas administrativas"
        M17VolunteerOpportunityType.CONSTRUCTION -> "Construcción"
        M17VolunteerOpportunityType.PROFESSIONAL_SUPPORT -> "Apoyo profesional"
        M17VolunteerOpportunityType.OTHER -> "Otra ayuda"
    }

    fun opportunityStatus(status: M17VolunteerOpportunityStatus): String = when (status) {
        M17VolunteerOpportunityStatus.DRAFT -> "Borrador"
        M17VolunteerOpportunityStatus.PUBLISHED -> "Abierta"
        M17VolunteerOpportunityStatus.PAUSED -> "Pausada"
        M17VolunteerOpportunityStatus.FILLED -> "Cupos completos"
        M17VolunteerOpportunityStatus.COMPLETED -> "Finalizada"
        M17VolunteerOpportunityStatus.CANCELLED -> "Cancelada"
    }

    fun applicationStatus(status: M17VolunteerApplicationStatus): String = when (status) {
        M17VolunteerApplicationStatus.SUBMITTED -> "Interés enviado"
        M17VolunteerApplicationStatus.REVIEWING -> "En revisión"
        M17VolunteerApplicationStatus.ACCEPTED -> "Aceptado"
        M17VolunteerApplicationStatus.REJECTED -> "No aceptado"
        M17VolunteerApplicationStatus.WITHDRAWN -> "Retirado"
        M17VolunteerApplicationStatus.COMPLETED -> "Completado"
    }

    fun quantityLine(quantity: Int, unit: String): String {
        val clean = unit.trim()
        return if (clean.isEmpty()) quantity.toString() else "$quantity $clean"
    }

    fun slotsLine(filled: Int, needed: Int): String? =
        if (needed <= 0) null else "$filled de $needed lugares"

    fun date(epochMs: Long): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    }
}
