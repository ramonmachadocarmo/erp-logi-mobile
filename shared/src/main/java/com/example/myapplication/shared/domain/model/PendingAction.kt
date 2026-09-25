package com.example.myapplication.shared.domain.model

enum class PendingActionType { DELIVER, FAIL }

/**
 * A delivery confirmation/failure the driver made while the server was unreachable, waiting to be
 * replayed against sales-service. Kept until the server acknowledges it.
 *
 * [rejectedReason] non-null means the server answered with a permanent 4xx (e.g. the order is
 * already delivered / no longer exists): it is NOT retried, but stays in the queue so the driver
 * can be told instead of the delivery silently vanishing.
 */
data class PendingAction(
    val id: String,
    val type: PendingActionType,
    val salesOrderId: String,
    val note: String = "",
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val rejectedReason: String? = null,
) {
    val isRejected: Boolean get() = rejectedReason != null
}
