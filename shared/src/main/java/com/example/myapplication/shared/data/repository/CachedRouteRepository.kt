package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.remote.ApiException
import com.example.myapplication.shared.domain.model.PendingAction
import com.example.myapplication.shared.domain.model.PendingActionType
import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.repository.PendingActionQueue
import com.example.myapplication.shared.domain.repository.RoutePlanCache
import com.example.myapplication.shared.domain.repository.RouteRepository
import java.time.LocalDate
import java.util.UUID

/**
 * Decorates [delegate] with offline support.
 *
 * Reads: every plan fetched successfully is saved, and when a read fails because the network is
 * unreachable ([ApiException.isNetwork]) the last saved plan for today is returned instead,
 * flagged with [RoutePlan.cachedAtMillis]. Server errors (4xx/5xx, incl. 401) are never masked.
 *
 * Writes: if confirming/failing a delivery fails for lack of network, the action is put in
 * [queue] and reported as success (the driver did it; it will reach the server later via
 * [onQueued] -> background sync). A confirmed stop is dropped from the cached plan, and returned
 * plans always hide stops with a queued DELIVER — even once back online but before the sync ran,
 * when the server still lists that order as undelivered. Server-side errors are not queued.
 */
class CachedRouteRepository(
    private val delegate: RouteRepository,
    private val cache: RoutePlanCache,
    private val queue: PendingActionQueue,
    private val onQueued: () -> Unit = {},
    private val today: () -> String = { LocalDate.now().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : RouteRepository {

    override suspend fun loadTodaysPlan(vehicleId: String): Result<RoutePlan?> {
        val result = delegate.loadTodaysPlan(vehicleId)
        result.onSuccess { plan ->
            if (plan != null) cache.save(today(), plan, clock()) else cache.removeVehicle(vehicleId)
        }
        return result.recoverOffline { cache.findByVehicle(vehicleId, today()) }.withQueueState()
    }

    override suspend fun refreshPlan(planId: String): Result<RoutePlan?> {
        val result = delegate.refreshPlan(planId)
        result.onSuccess { plan -> if (plan != null) cache.save(today(), plan, clock()) }
        return result.recoverOffline { cache.findByPlanId(planId, today()) }.withQueueState()
    }

    override suspend fun selectOption(planId: String, option: RouteOption): Result<RoutePlan> =
        delegate.selectOption(planId, option)
            .onSuccess { cache.save(today(), it, clock()) }
            .map { it.withQueueState() }

    override suspend fun confirmArrival(salesOrderId: String): Result<Unit> {
        val result = delegate.confirmArrival(salesOrderId)
        if (result.isSuccess) {
            cache.removeStop(salesOrderId)
            return result
        }
        if (!result.isNetworkFailure()) return result
        enqueue(PendingActionType.DELIVER, salesOrderId, note = "")
        cache.removeStop(salesOrderId)
        return Result.success(Unit)
    }

    override suspend fun failDelivery(salesOrderId: String, note: String): Result<Unit> {
        val result = delegate.failDelivery(salesOrderId, note)
        if (result.isSuccess || !result.isNetworkFailure()) return result
        enqueue(PendingActionType.FAIL, salesOrderId, note)
        return Result.success(Unit)
    }

    private fun enqueue(type: PendingActionType, salesOrderId: String, note: String) {
        queue.enqueue(PendingAction(newId(), type, salesOrderId, note, createdAtMillis = clock()))
        onQueued()
    }

    private fun Result<*>.isNetworkFailure() = (exceptionOrNull() as? ApiException)?.isNetwork == true

    /** Replaces a network failure with the cached value; keeps the original failure on a cache miss. */
    private inline fun Result<RoutePlan?>.recoverOffline(lookup: () -> RoutePlan?): Result<RoutePlan?> {
        if (!isNetworkFailure()) return this
        val cached = lookup() ?: return this
        return Result.success(cached)
    }

    private fun Result<RoutePlan?>.withQueueState(): Result<RoutePlan?> = map { it?.withQueueState() }

    private fun RoutePlan.withQueueState(): RoutePlan {
        val pending = queue.pending()
        val delivered = pending.filter { it.type == PendingActionType.DELIVER }.map { it.salesOrderId }.toSet()
        return copy(
            stops = stops.filter { it.salesOrderId !in delivered },
            pendingSyncCount = pending.size,
            rejectedSyncCount = queue.rejected().size,
        )
    }
}
