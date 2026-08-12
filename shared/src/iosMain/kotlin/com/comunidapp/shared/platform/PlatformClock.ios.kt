package com.comunidapp.shared.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun defaultNowEpochMs(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
