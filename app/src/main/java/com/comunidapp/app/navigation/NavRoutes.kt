package com.comunidapp.app.navigation

object NavRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val FORGOT_PASSWORD = "forgot_password"
    const val EMAIL_VERIFICATION = "email_verification/{email}"
    const val LEGAL_TERMS = "legal_terms"
    const val LEGAL_PRIVACY = "legal_privacy"

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
    const val ADMIN_MODERATION = "admin_moderation"

    const val ARG_CONVERSATION_ID = "conversationId"
    const val ARG_PEER_NAME = "peerName"
    const val ARG_ADOPTION_ID = "adoptionId"
    const val ARG_SHELTER_ID = "shelterId"
    const val ARG_PET_ID = "petId"
    const val ARG_EMAIL = "email"
    const val ARG_USER_ID = "userId"
    const val ARG_SERVICE_ID = "serviceId"

    fun adoptionDetail(adoptionId: String) = "adoption_detail/$adoptionId"
    fun shelterDetail(shelterId: String) = "shelter_detail/$shelterId"
    fun serviceDetail(serviceId: String) =
        "service_detail/${java.net.URLEncoder.encode(serviceId, Charsets.UTF_8.name())}"
    fun petDetail(petId: String) = "pet_detail/$petId"
    fun emailVerification(email: String) = "email_verification/$email"
    fun editPet(petId: String) = "edit_pet/$petId"
    fun userProfile(userId: String) =
        "user_profile/${java.net.URLEncoder.encode(userId, Charsets.UTF_8.name())}"
    fun chatThread(conversationId: String, peerName: String) =
        "chat_thread/${java.net.URLEncoder.encode(conversationId, Charsets.UTF_8.name())}/" +
            java.net.URLEncoder.encode(peerName, Charsets.UTF_8.name())
    fun chatStart(userId: String, peerName: String) =
        "chat_start/${java.net.URLEncoder.encode(userId, Charsets.UTF_8.name())}/" +
            java.net.URLEncoder.encode(peerName, Charsets.UTF_8.name())
}
