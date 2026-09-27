package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.auth.PostAuthDestination
import com.comunidapp.app.domain.auth.PostAuthResolver

    /**
     * Post-session routing must not run on a JWT stub while PERSON is unknown.
     * Fresh APK / empty cache: stay LOADING until PERSON fetch finishes.
     * A row from PERSON (personResolved=true) routes Home or Completar perfil;
     * it never stays LOADING even if the row still looks like a JWT stub.
     */
object SessionPersonRouting {
    enum class Decision {
        LOADING,
        HOME,
        COMPLETE_PROFILE
    }

    /**
     * [SupabaseAuthRepository] UserInfo.toUser() has IN_PROGRESS, no birthDate,
     * no homeLocalityId. That is not a PERSON row and must not route to
     * Completar perfil.
     */
    fun isJwtStub(user: User?): Boolean {
        if (user == null) return false
        val noBirth = user.birthDate.isNullOrBlank()
        val noLocality = user.homeLocalityId.isNullOrBlank()
        val completed = user.onboardingStatus.equals(
            ProfileSetupStatus.COMPLETED.name,
            ignoreCase = true
        )
        return noBirth && noLocality && !completed
    }

    fun decide(person: User?, personResolved: Boolean): Decision {
        if (!personResolved) return Decision.LOADING
        if (person == null) return Decision.COMPLETE_PROFILE
        return if (PostAuthResolver.destination(person) == PostAuthDestination.MAIN_APP) {
            Decision.HOME
        } else {
            Decision.COMPLETE_PROFILE
        }
    }
}
