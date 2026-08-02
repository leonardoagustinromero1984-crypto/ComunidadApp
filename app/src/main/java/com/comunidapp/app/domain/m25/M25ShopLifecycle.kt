package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.model.M25ShopStatus

object M25ShopLifecycle {
    fun validateTransition(
        current: M25ShopStatus,
        target: M25ShopStatus,
        hasActiveProduct: Boolean = false
    ): String? {
        if (current == target) return null
        if (target == M25ShopStatus.ARCHIVED || target == M25ShopStatus.CLOSED) return null
        if (current == M25ShopStatus.ARCHIVED || current == M25ShopStatus.CLOSED) return "M25_ARCHIVED_SHOP"
        return when {
            current == M25ShopStatus.DRAFT && target == M25ShopStatus.ACTIVE ->
                if (hasActiveProduct) null else "M25_SHOP_NOT_READY_TO_PUBLISH"
            current == M25ShopStatus.ACTIVE && target == M25ShopStatus.PAUSED -> null
            current == M25ShopStatus.PAUSED && target == M25ShopStatus.ACTIVE -> null
            current == M25ShopStatus.ACTIVE && target == M25ShopStatus.SUSPENDED -> null
            current == M25ShopStatus.SUSPENDED && target == M25ShopStatus.ACTIVE -> null
            current == M25ShopStatus.ACTIVE && target == M25ShopStatus.CLOSED -> null
            else -> "M25_INVALID_STATUS_TRANSITION"
        }
    }
}
