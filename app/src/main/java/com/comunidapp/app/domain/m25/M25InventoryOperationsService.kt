package com.comunidapp.app.domain.m25

/** LeoVer M25 — stock disponible, reservado y comprometido (sin confiar en Android). */
data class M25InventorySnapshot(
    val productId: String,
    val totalQuantity: Int,
    val reservedQuantity: Int,
    val committedQuantity: Int = 0
) {
    val availableQuantity: Int = (totalQuantity - reservedQuantity - committedQuantity).coerceAtLeast(0)
}

object M25InventoryOperationsService {
    fun available(snapshot: M25InventorySnapshot): Int = snapshot.availableQuantity

    fun reserve(
        snapshot: M25InventorySnapshot,
        quantity: Int,
        reservationKey: String?,
        existingKeys: Set<String>
    ): Result<Pair<M25InventorySnapshot, Set<String>>> {
        if (quantity <= 0) return Result.failure(IllegalStateException("M25_INVALID_QUANTITY"))
        reservationKey?.let { key ->
            if (key in existingKeys) return Result.success(snapshot to existingKeys)
        }
        if (snapshot.availableQuantity < quantity) return Result.failure(IllegalStateException("M25_OUT_OF_STOCK"))
        val updated = snapshot.copy(reservedQuantity = snapshot.reservedQuantity + quantity)
        val keys = reservationKey?.let { existingKeys + it } ?: existingKeys
        return Result.success(updated to keys)
    }

    fun release(snapshot: M25InventorySnapshot, quantity: Int): Result<M25InventorySnapshot> {
        if (quantity <= 0) return Result.success(snapshot)
        val releaseQty = minOf(quantity, snapshot.reservedQuantity)
        return Result.success(snapshot.copy(reservedQuantity = snapshot.reservedQuantity - releaseQty))
    }

    fun commit(snapshot: M25InventorySnapshot, quantity: Int): Result<M25InventorySnapshot> {
        if (quantity <= 0) return Result.success(snapshot)
        val commitQty = minOf(quantity, snapshot.reservedQuantity)
        return Result.success(
            snapshot.copy(
                totalQuantity = snapshot.totalQuantity - commitQty,
                reservedQuantity = snapshot.reservedQuantity - commitQty,
                committedQuantity = snapshot.committedQuantity + commitQty
            )
        )
    }

    fun replenish(snapshot: M25InventorySnapshot, quantity: Int): Result<M25InventorySnapshot> {
        if (quantity <= 0) return Result.failure(IllegalStateException("M25_INVALID_QUANTITY"))
        return Result.success(snapshot.copy(totalQuantity = snapshot.totalQuantity + quantity))
    }

    fun adjust(snapshot: M25InventorySnapshot, newTotal: Int): Result<M25InventorySnapshot> {
        if (newTotal < snapshot.reservedQuantity + snapshot.committedQuantity) {
            return Result.failure(IllegalStateException("M25_INVALID_STOCK"))
        }
        return Result.success(snapshot.copy(totalQuantity = newTotal))
    }
}
