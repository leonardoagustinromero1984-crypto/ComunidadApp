package com.comunidapp.app.navigation

object NavRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val FORGOT_PASSWORD = "forgot_password"
    const val EMAIL_VERIFICATION = "email_verification/{email}"

    const val HOME = "home"
    const val ADOPTIONS = "adoptions"
    const val PUBLISH = "publish"
    const val SHELTERS = "shelters"
    const val PROFILE = "profile"
    const val MY_PETS = "my_pets"
    const val LOST_FOUND = "lost_found"

    const val ADOPTION_DETAIL = "adoption_detail/{adoptionId}"
    const val SHELTER_DETAIL = "shelter_detail/{shelterId}"
    const val PET_DETAIL = "pet_detail/{petId}"

    const val EDIT_PROFILE = "edit_profile"
    const val USER_PROFILE = "user_profile/{userId}"

    const val PUBLISH_GENERAL = "publish_general"
    const val PUBLISH_ADOPTION = "publish_adoption"
    const val PUBLISH_LOST_FOUND = "publish_lost_found"

    const val ARG_ADOPTION_ID = "adoptionId"
    const val ARG_SHELTER_ID = "shelterId"
    const val ARG_PET_ID = "petId"
    const val ARG_EMAIL = "email"
    const val ARG_USER_ID = "userId"

    fun adoptionDetail(adoptionId: String) = "adoption_detail/$adoptionId"
    fun shelterDetail(shelterId: String) = "shelter_detail/$shelterId"
    fun petDetail(petId: String) = "pet_detail/$petId"
    fun emailVerification(email: String) = "email_verification/$email"
    fun userProfile(userId: String) = "user_profile/$userId"
}
