package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.model.M25OrderStatus
import com.comunidapp.app.data.model.M25ReturnStatus

object M25OrderOperationsService {
    fun validateOrderTransition(current: M25OrderStatus, target: M25OrderStatus): String? {
        if (current == target) return null
        if (current.isTerminal()) return "M25_ORDER_TERMINAL"
        return when (current) {
            M25OrderStatus.DRAFT -> if (target == M25OrderStatus.SUBMITTED || target == M25OrderStatus.CANCELLED) null else "M25_INVALID_ORDER_TRANSITION"
            M25OrderStatus.SUBMITTED -> if (target in setOf(M25OrderStatus.ACCEPTED, M25OrderStatus.CANCELLED)) null else "M25_INVALID_ORDER_TRANSITION"
            M25OrderStatus.ACCEPTED -> if (target in setOf(M25OrderStatus.PREPARING, M25OrderStatus.CANCELLED)) null else "M25_INVALID_ORDER_TRANSITION"
            M25OrderStatus.PREPARING -> if (target in setOf(M25OrderStatus.SHIPPED, M25OrderStatus.CANCELLED)) null else "M25_INVALID_ORDER_TRANSITION"
            M25OrderStatus.SHIPPED -> if (target == M25OrderStatus.DELIVERED) null else "M25_INVALID_ORDER_TRANSITION"
            M25OrderStatus.DELIVERED -> if (target == M25OrderStatus.RETURN_REQUESTED) null else "M25_INVALID_ORDER_TRANSITION"
            M25OrderStatus.RETURN_REQUESTED -> if (target == M25OrderStatus.RETURNED) null else "M25_INVALID_ORDER_TRANSITION"
            else -> "M25_INVALID_ORDER_TRANSITION"
        }
    }

    fun validateReturnTransition(current: M25ReturnStatus, target: M25ReturnStatus): String? {
        if (current == target) return null
        if (current == M25ReturnStatus.CLOSED) return "M25_RETURN_TERMINAL"
        return when (current) {
            M25ReturnStatus.REQUESTED -> if (target in setOf(M25ReturnStatus.APPROVED, M25ReturnStatus.REJECTED)) null else "M25_INVALID_RETURN_TRANSITION"
            M25ReturnStatus.APPROVED -> if (target == M25ReturnStatus.RECEIVED) null else "M25_INVALID_RETURN_TRANSITION"
            M25ReturnStatus.RECEIVED -> if (target == M25ReturnStatus.CLOSED) null else "M25_INVALID_RETURN_TRANSITION"
            else -> "M25_INVALID_RETURN_TRANSITION"
        }
    }

    private fun M25OrderStatus.isTerminal(): Boolean =
        this in setOf(M25OrderStatus.CANCELLED, M25OrderStatus.RETURNED)
}
