package com.comunidapp.app.domain.schedule

object AppointmentSlotPolicy {
    val INTERVAL_MINUTES: List<Int> = listOf(15, 20, 30, 45, 60)
    const val DEFAULT_INTERVAL_MINUTES = 30

    fun isAllowed(minutes: Int): Boolean = minutes in INTERVAL_MINUTES

    fun label(minutes: Int): String = "$minutes min"
}
