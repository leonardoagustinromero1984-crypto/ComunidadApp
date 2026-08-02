package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.model.M25Promotion
import com.comunidapp.app.data.model.M25PromotionStatus
import com.comunidapp.app.data.model.M25PromotionType

object M25PromotionCalculator {
    fun selectBest(
        promotions: List<M25Promotion>,
        code: String?,
        subtotalCents: Long,
        now: Long = System.currentTimeMillis()
    ): Pair<M25Promotion?, Long> {
        val active = promotions.filter { it.status == M25PromotionStatus.ACTIVE && now in it.startsAt..it.endsAt }
        val byCode = code?.trim()?.uppercase()?.let { c -> active.firstOrNull { it.code == c } }
        val chosen = byCode ?: active.maxByOrNull { discountFor(it, subtotalCents) }
        val discount = chosen?.let { discountFor(it, subtotalCents) } ?: 0L
        return chosen to discount.coerceAtMost(subtotalCents).coerceAtLeast(0)
    }

    fun discountFor(promo: M25Promotion, subtotalCents: Long): Long = when (promo.type) {
        M25PromotionType.PERCENTAGE -> subtotalCents * promo.value / 100
        M25PromotionType.FIXED_AMOUNT -> minOf(subtotalCents, promo.value)
    }

    /** Promociones incompatibles: solo una activa por pedido. */
    fun incompatibleAccumulation(selected: List<M25Promotion>): Boolean = selected.size > 1
}
