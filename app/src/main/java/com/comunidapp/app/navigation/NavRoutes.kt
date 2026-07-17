package com.comunidapp.app.navigation

object NavRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val FORGOT_PASSWORD = "forgot_password"
    const val EMAIL_VERIFICATION = "email_verification/{email}"
    const val LEGAL_TERMS = "legal_terms"
    const val LEGAL_PRIVACY = "legal_privacy"
    const val ACCOUNT_SECURITY = "account_security"
    const val PASSWORD_RESET_ACTIVE = "password_reset_active"
    const val LEGAL_CONSENT_REQUIRED = "legal_consent_required"
    const val PROFILE_ONBOARDING = "profile_onboarding"
    const val ACCOUNT_ACCESS_BLOCKED = "account_access_blocked"

    const val HOME = "home"
    const val SUMATE = "sumate"
    const val COMUNIDAD = "comunidad"
    const val MY_BUSINESS = "my_business"
    const val PUBLISH = "publish"
    const val PROFILE = "profile"
    const val ADOPTIONS = "adoptions"
    const val SHELTERS = "shelters"
    const val MY_PETS = "my_pets"
    const val LOST_FOUND = "lost_found"

    const val ADOPTION_DETAIL = "adoption_detail/{adoptionId}"
    const val SHELTER_DETAIL = "shelter_detail/{shelterId}"
    const val PET_DETAIL = "pet_detail/{petId}"

    const val EDIT_PROFILE = "edit_profile"
    const val SEARCH_FRIENDS = "search_friends"
    const val USER_PROFILE = "user_profile/{userId}"

    const val MY_ORGANIZATIONS = "my_organizations"
    const val CREATE_ORGANIZATION = "create_organization"
    const val EDIT_ORGANIZATION = "edit_organization/{organizationId}"
    const val MANAGE_ORGANIZATION = "manage_organization/{organizationId}"
    const val ORGANIZATION_TEAM = "organization_team/{organizationId}"
    const val ORGANIZATION_BRANCHES = "organization_branches/{organizationId}"
    const val PUBLIC_ORGANIZATION = "public_organization/{slug}"

    const val ADD_PET = "add_pet"
    const val EDIT_PET = "edit_pet/{petId}"

    const val PUBLISH_GENERAL = "publish_general"
    const val PUBLISH_QUESTION = "publish_question"
    const val PUBLISH_PROMO = "publish_promo"
    const val PUBLISH_ADOPTION = "publish_adoption"
    const val PUBLISH_LOST_FOUND = "publish_lost_found"
    const val PUBLISH_URGENT = "publish_urgent"
    const val SEARCH = "search"
    const val MY_ADOPTIONS = "my_adoptions"
    const val LOST_FOUND_MAP = "lost_found_map"
    const val CHAT = "chat"
    const val FRIEND_REQUESTS = "friend_requests"
    const val CHAT_THREAD = "chat_thread/{conversationId}/{peerName}"
    const val CHAT_START = "chat_start/{userId}/{peerName}"
    const val PUBLISH_FOSTER = "publish_foster"
    const val PUBLISH_EVENT = "publish_event"
    const val PUBLISH_DONATION = "publish_donation"
    const val PUBLISH_SHELTER = "publish_shelter"
    const val SERVICE_DETAIL = "service_detail/{serviceId}"
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATION_PREFERENCES = "notification_preferences"
    const val ADMIN_MODERATION = "admin_moderation"
    const val PLATFORM_ADMIN = "platform_admin"
    const val MODERATION_REPORT_DETAIL = "moderation_report/{reportId}"
    const val MODERATION_CASES = "moderation_cases"
    const val MODERATION_CASE_DETAIL = "moderation_case/{caseId}"
    const val MODERATION_APPEALS = "moderation_appeals"
    const val MODERATION_APPEAL_DETAIL = "moderation_appeal/{appealId}"
    const val MY_MODERATION_APPEALS = "my_moderation_appeals"
    const val ORG_VERIFICATION_QUEUE = "org_verification_queue"
    const val ORG_VERIFICATION_REVIEW = "org_verification_review/{reviewId}"
    const val MY_SUPPORT_TICKETS = "my_support_tickets"
    const val CREATE_SUPPORT_TICKET = "create_support_ticket"
    const val SUPPORT_TICKET_DETAIL = "support_ticket/{ticketId}"
    const val SUPPORT_ADMIN_QUEUE = "support_admin_queue"
    const val SUPPORT_ADMIN_TICKET = "support_admin_ticket/{ticketId}"
    const val ADMINISTRATIVE_AUDIT = "administrative_audit"
    const val ADMINISTRATIVE_OPS_HUB = "administrative_ops_hub"
    const val OBSERVABILITY_OVERVIEW = "observability_overview"
    const val OBSERVABILITY_METRICS = "observability_metrics"
    const val OBSERVABILITY_HEALTH = "observability_health"
    const val OBSERVABILITY_INCIDENTS = "observability_incidents"
    const val OBSERVABILITY_AUDIT = "observability_audit"
    const val OBSERVABILITY_ERRORS = "observability_errors"
    const val OBSERVABILITY_EXPORTS = "observability_exports"
    const val OBSERVABILITY_RETENTION = "observability_retention"
    const val OBSERVABILITY_PERMISSIONS_INFO = "observability_permissions_info"

    const val ARG_REPORT_ID = "reportId"
    const val ARG_CASE_ID = "caseId"
    const val ARG_APPEAL_ID = "appealId"
    const val ARG_REVIEW_ID = "reviewId"
    const val ARG_TICKET_ID = "ticketId"

    const val ARG_CONVERSATION_ID = "conversationId"
    const val ARG_PEER_NAME = "peerName"
    const val ARG_ADOPTION_ID = "adoptionId"
    const val ARG_SHELTER_ID = "shelterId"
    const val ARG_PET_ID = "petId"
    const val ARG_EMAIL = "email"
    const val ARG_USER_ID = "userId"
    const val ARG_SERVICE_ID = "serviceId"
    const val ARG_ORGANIZATION_ID = "organizationId"
    const val ARG_SLUG = "slug"

    fun adoptionDetail(adoptionId: String) = "adoption_detail/$adoptionId"
    fun shelterDetail(shelterId: String) = "shelter_detail/$shelterId"
    fun serviceDetail(serviceId: String) =
        "service_detail/${java.net.URLEncoder.encode(serviceId, Charsets.UTF_8.name())}"
    fun petDetail(petId: String) = "pet_detail/$petId"
    fun emailVerification(email: String) = "email_verification/$email"
    fun editPet(petId: String) = "edit_pet/$petId"
    fun userProfile(userId: String) =
        "user_profile/${java.net.URLEncoder.encode(userId, Charsets.UTF_8.name())}"
    fun editOrganization(organizationId: String) =
        "edit_organization/${java.net.URLEncoder.encode(organizationId, Charsets.UTF_8.name())}"
    fun manageOrganization(organizationId: String) =
        "manage_organization/${java.net.URLEncoder.encode(organizationId, Charsets.UTF_8.name())}"
    fun organizationTeam(organizationId: String) =
        "organization_team/${java.net.URLEncoder.encode(organizationId, Charsets.UTF_8.name())}"
    fun organizationBranches(organizationId: String) =
        "organization_branches/${java.net.URLEncoder.encode(organizationId, Charsets.UTF_8.name())}"
    fun publicOrganization(slug: String) =
        "public_organization/${java.net.URLEncoder.encode(slug, Charsets.UTF_8.name())}"
    fun chatThread(conversationId: String, peerName: String) =
        "chat_thread/${java.net.URLEncoder.encode(conversationId, Charsets.UTF_8.name())}/" +
            java.net.URLEncoder.encode(peerName, Charsets.UTF_8.name())
    fun chatStart(userId: String, peerName: String) =
        "chat_start/${java.net.URLEncoder.encode(userId, Charsets.UTF_8.name())}/" +
            java.net.URLEncoder.encode(peerName, Charsets.UTF_8.name())

    fun moderationReportDetail(reportId: String) =
        "moderation_report/${java.net.URLEncoder.encode(reportId, Charsets.UTF_8.name())}"
    fun moderationCaseDetail(caseId: String) =
        "moderation_case/${java.net.URLEncoder.encode(caseId, Charsets.UTF_8.name())}"
    fun moderationAppealDetail(appealId: String) =
        "moderation_appeal/${java.net.URLEncoder.encode(appealId, Charsets.UTF_8.name())}"
    fun orgVerificationReview(reviewId: String) =
        "org_verification_review/${java.net.URLEncoder.encode(reviewId, Charsets.UTF_8.name())}"
    fun supportTicketDetail(ticketId: String) =
        "support_ticket/${java.net.URLEncoder.encode(ticketId, Charsets.UTF_8.name())}"
    fun supportAdminTicket(ticketId: String) =
        "support_admin_ticket/${java.net.URLEncoder.encode(ticketId, Charsets.UTF_8.name())}"
}
