package com.comunidapp.shared.remote

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.timeIntervalSince1970

internal actual fun parseIso8601ToEpochMs(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    val formatter = NSISO8601DateFormatter()
    val date: NSDate = formatter.dateFromString(iso.trim()) ?: return 0L
    return (date.timeIntervalSince1970 * 1000.0).toLong()
}
