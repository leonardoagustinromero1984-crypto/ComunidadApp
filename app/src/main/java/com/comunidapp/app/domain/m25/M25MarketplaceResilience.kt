package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.repository.M25MarketplaceException
import com.comunidapp.app.data.repository.M25MarketplaceErrors

object M25MarketplaceResilience {
    fun safeUserMessage(error: Throwable): String = when (error) {
        is M25MarketplaceException -> error.message ?: M25MarketplaceErrors.userMessage(error.code)
        else -> M25MarketplaceErrors.userMessage("UNKNOWN")
    }
}
