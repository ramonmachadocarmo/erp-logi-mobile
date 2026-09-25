package com.example.myapplication.shared.domain.model

import kotlin.math.roundToInt

/** Delivery-plan lifecycle, mirrors sales-service's DeliveryPlan.Status values. */
object PlanStatus {
    const val PLANNED = "PLANNED"
    const val CONFIRMED = "CONFIRMED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val CANCELLED = "CANCELLED"
}

/**
 * A driver's route for one vehicle/day, backed by sales-service's DeliveryPlan. [stops] only
 * contains sales orders not yet DELIVERED (RouteRepositoryImpl filters against the live order
 * list on every load, since the plan itself doesn't track per-stop delivery state) — an already
 * completed plan (no pending stops left) still has [id]/[status] so the UI can tell "finished"
 * apart from "nothing planned today".
 */
data class RoutePlan(
    val id: String,
    val vehicleId: String,
    val vehicleName: String,
    val vehicleCode: String,
    val centerName: String,
    val centerLat: Double,
    val centerLng: Double,
    val status: String,
    val distanceM: Double,
    val durationS: Double,
    val geometry: List<List<Double>>,
    val stops: List<Stop>,
    val options: List<RouteOption>,
    /**
     * Epoch millis of when this plan was last fetched from sales-service, set only when it was
     * served from the offline cache (network unreachable). Null = fresh from the server.
     */
    val cachedAtMillis: Long? = null,
    /** Deliveries/failures confirmed offline and not yet acknowledged by the server. */
    val pendingSyncCount: Int = 0,
    /** Queued actions the server permanently refused; the driver should be told. */
    val rejectedSyncCount: Int = 0,
) {
    val isFromCache: Boolean get() = cachedAtMillis != null

    val totalTimeMinutes: Int get() = (durationS / 60.0).roundToInt()
    val totalDistanceKm: Double get() = distanceM / 1000.0

    /** More than one real alternative to compare — mirrors the old mock's Comparison screen. */
    val hasAlternatives: Boolean get() = status == PlanStatus.PLANNED && options.size > 1
}
