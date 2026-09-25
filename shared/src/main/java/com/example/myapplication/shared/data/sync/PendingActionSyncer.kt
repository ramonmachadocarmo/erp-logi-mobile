package com.example.myapplication.shared.data.sync

import com.example.myapplication.shared.data.remote.ApiException
import com.example.myapplication.shared.domain.model.PendingActionType
import com.example.myapplication.shared.domain.repository.PendingActionQueue
import com.example.myapplication.shared.domain.repository.RouteRepository

/** What one sync pass achieved and whether the caller should schedule another. */
data class SyncOutcome(
    val sent: Int = 0,
    val rejected: Int = 0,
    /** Still queued and worth retrying (offline, 5xx, timeouts...). */
    val retryLater: Boolean = false,
    /** Server said 401: nothing can be sent until the driver logs in again. */
    val needsLogin: Boolean = false,
)

/**
 * Replays queued offline writes, oldest first, against the *uncached* [remote] repository (the
 * cached one would just re-queue a failure).
 *
 * - Success -> removed from the queue.
 * - No network -> stop right away (the rest would fail too), retry later.
 * - 401 -> stop, keep everything, [SyncOutcome.needsLogin].
 * - 408/429/5xx or unexpected errors -> counted, kept, retried later; other actions still go out.
 * - Any other 4xx -> the server refused it for good (e.g. order already delivered): marked
 *   rejected, never retried, kept for the UI to report.
 */
class PendingActionSyncer(
    private val queue: PendingActionQueue,
    private val remote: RouteRepository,
) {
    suspend fun sync(): SyncOutcome {
        var sent = 0
        var rejected = 0
        var retry = false
        for (action in queue.pending()) {
            val result = when (action.type) {
                PendingActionType.DELIVER -> remote.confirmArrival(action.salesOrderId)
                PendingActionType.FAIL -> remote.failDelivery(action.salesOrderId, action.note)
            }
            val err = result.exceptionOrNull()
            if (err == null) {
                queue.remove(action.id)
                sent++
                continue
            }
            val message = err.message ?: "erro"
            val api = err as? ApiException
            when {
                api == null -> {
                    queue.recordFailure(action.id, message)
                    retry = true
                }
                api.isNetwork -> {
                    queue.recordFailure(action.id, message)
                    return SyncOutcome(sent, rejected, retryLater = true)
                }
                api.statusCode == 401 -> return SyncOutcome(sent, rejected, needsLogin = true)
                api.statusCode == 408 || api.statusCode == 429 || (api.statusCode ?: 0) >= 500 -> {
                    queue.recordFailure(action.id, message)
                    retry = true
                }
                else -> {
                    queue.markRejected(action.id, message)
                    rejected++
                }
            }
        }
        return SyncOutcome(sent, rejected, retryLater = retry)
    }
}
