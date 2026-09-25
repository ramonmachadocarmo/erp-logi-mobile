package com.example.myapplication.shared.domain.repository

import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan

interface RouteRepository {
    /** This vehicle's plan for today, with already-delivered stops filtered out. Null = nothing planned. */
    suspend fun loadTodaysPlan(vehicleId: String): Result<RoutePlan?>

    /** Re-fetches the same plan by id — e.g. after the dispatcher changed it, or a manual refresh. */
    suspend fun refreshPlan(planId: String): Result<RoutePlan?>

    /** Picks one of the plan's server-computed route alternatives and confirms it. */
    suspend fun selectOption(planId: String, option: RouteOption): Result<RoutePlan>

    /** Marks the current stop's order as delivered (sales-service: POST /sales-orders/:id/deliver). */
    suspend fun confirmArrival(salesOrderId: String): Result<Unit>

    /** Marks the current stop's order as a failed delivery attempt, to retry later. */
    suspend fun failDelivery(salesOrderId: String, note: String): Result<Unit>
}
