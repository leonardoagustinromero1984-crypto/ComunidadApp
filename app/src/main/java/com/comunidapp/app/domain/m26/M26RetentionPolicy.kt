package com.comunidapp.app.domain.m26

/** Retención documentada — borrado masivo requiere scheduler/admin futuro. */
object M26RetentionPolicy {
    const val FAILED_JOB_DAYS = 30
    const val ASSISTANCE_SESSION_DAYS = 90
    const val REJECTED_RESULT_DAYS = 180
    const val ARCHIVED_CANDIDATE_DAYS = 365
}
