package com.comunidapp.app.domain.m17

/**
 * Public money. Amounts stay in minor units. The symbol "$" is only used for ARS.
 */
object MoneyPresentation {
    fun formatMinor(amountMinor: Long, currency: String): String {
        val negative = amountMinor < 0
        val abs = kotlin.math.abs(amountMinor)
        val major = abs / 100
        val cents = (abs % 100).toInt()
        val grouped = groupThousands(major)
        val number = if (cents == 0) grouped else "$grouped,${cents.toString().padStart(2, '0')}"
        val code = currency.trim().uppercase()
        val body = if (code.isEmpty() || code == "ARS") "$$number" else "$code $number"
        return if (negative) "-$body" else body
    }

    fun raisedOfGoal(confirmedMinor: Long, goalMinor: Long, currency: String): String =
        "${formatMinor(confirmedMinor, currency)} de ${formatMinor(goalMinor, currency)}"

    /** Visual bar only. The confirmed amount is never replaced by this fraction. */
    fun barFraction(progressPercent: Int): Float =
        progressPercent.coerceIn(0, 100) / 100f

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        val out = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) out.append('.')
            out.append(char)
        }
        return out.toString()
    }
}
