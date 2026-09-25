package com.example.myapplication.shared.domain.model

/**
 * One delivery stop, backed by sales-service's PlanStop — lat/lng and address come from the
 * server's own resolveGeo/OSRM step at plan creation, not re-geocoded on-device.
 */
data class Stop(
    val salesOrderId: String,
    val seq: Int,
    val customerName: String,
    val address: Address,
    val lat: Double,
    val lng: Double,
    val distanceM: Double,
    val durationS: Double,
)
