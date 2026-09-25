package com.example.myapplication.shared

import com.example.myapplication.shared.domain.model.Address
import com.example.myapplication.shared.domain.model.PlanStatus
import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Stop
import com.example.myapplication.shared.domain.model.Vehicle

fun stop(orderId: String, seq: Int = 1) = Stop(
    salesOrderId = orderId, seq = seq, customerName = "Cliente $orderId",
    address = Address(street = "Rua A", number = "10", complement = "ap 2", district = "Centro", city = "Manaus", state = "AM", zip = "69000-000"),
    lat = -3.11, lng = -60.01, distanceM = 1000.0, durationS = 200.0,
)

fun plan(
    id: String = "plan1",
    vehicleId: String = "veh1",
    stops: List<Stop> = listOf(stop("o1", 1), stop("o2", 2)),
    options: List<RouteOption> = emptyList(),
) = RoutePlan(
    id = id, vehicleId = vehicleId, vehicleName = "Van", vehicleCode = "V01",
    centerName = "CD Manaus", centerLat = -3.1, centerLng = -60.0,
    status = PlanStatus.CONFIRMED, distanceM = 15000.0, durationS = 2700.0,
    geometry = listOf(listOf(-3.1, -60.0), listOf(-3.13, -60.02)),
    stops = stops, options = options,
)

fun vehicle(id: String = "veh1") = Vehicle(id, "V01", "Van", 1200.5, 8.25, true)
