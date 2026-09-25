package com.example.myapplication.shared.domain.repository

import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Vehicle

/** Local copy of the last plan fetched per vehicle, so a route survives a dead zone. */
interface RoutePlanCache {
    /** Stores [plan] as the plan for its vehicle on [date] (YYYY-MM-DD), replacing any previous one. */
    fun save(date: String, plan: RoutePlan, savedAtMillis: Long)

    /** Cached plan for [vehicleId] on [date], flagged via [RoutePlan.cachedAtMillis]; null if none/other day. */
    fun findByVehicle(vehicleId: String, date: String): RoutePlan?

    /** Cached plan with this id saved on [date]; null if none/other day. */
    fun findByPlanId(planId: String, date: String): RoutePlan?

    fun removeVehicle(vehicleId: String)

    /** Drops [salesOrderId] from every cached plan's pending stops (it was just delivered). */
    fun removeStop(salesOrderId: String)

    fun clear()
}

/** Last vehicle list from config-service — needed to get past the vehicle picker offline. */
interface VehicleCache {
    fun save(vehicles: List<Vehicle>)
    fun read(): List<Vehicle>?
    fun clear()
}
