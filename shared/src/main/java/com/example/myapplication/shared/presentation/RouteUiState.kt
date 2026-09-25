package com.example.myapplication.shared.presentation

import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Vehicle

/** Shared by MyCarAppScreen (Android Auto) and MainActivity (phone) — same flow, two renderers. */
sealed interface RouteUiState {
    object Restoring : RouteUiState

    data class LoginRequired(val error: String? = null, val busy: Boolean = false) : RouteUiState

    /** Logged in, but the role doesn't have view access to both Rotas and Entrega. */
    data class NoAccess(val userName: String) : RouteUiState

    data class VehiclePicker(
        val vehicles: List<Vehicle>,
        val userName: String,
        val error: String? = null,
        val busy: Boolean = false,
    ) : RouteUiState

    data class NoRouteToday(val vehicle: Vehicle) : RouteUiState

    /** Plan is PLANNED with more than one server-computed alternative — pick one before driving. */
    data class RouteOptions(val vehicle: Vehicle, val plan: RoutePlan, val busy: Boolean = false) : RouteUiState

    /** The working screen: map + stop list + confirm/fail-arrival actions. */
    data class ActiveRoute(
        val vehicle: Vehicle,
        val plan: RoutePlan,
        val busy: Boolean = false,
        val error: String? = null,
    ) : RouteUiState

    data class RouteCompleted(val vehicle: Vehicle, val plan: RoutePlan) : RouteUiState

    data class Error(val message: String) : RouteUiState
}
