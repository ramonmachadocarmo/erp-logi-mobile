package com.example.myapplication.shared.presentation

import com.example.myapplication.shared.AppContainer
import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives the whole login -> pick vehicle -> load route -> (compare options) -> drive -> confirm
 * arrival flow. One instance of this backs both MyCarAppScreen (Android Auto) and MainActivity
 * (phone) — same orchestration, two renderers — instead of each screen re-implementing it.
 */
class RouteFlowController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val onStateChanged: (RouteUiState) -> Unit,
) {
    var state: RouteUiState = RouteUiState.Restoring
        private set(value) {
            field = value
            onStateChanged(value)
        }

    private var userName: String = ""

    fun start() {
        state = RouteUiState.Restoring
        scope.launch { restore() }
    }

    private suspend fun restore() {
        val session = container.restoreSessionUseCase().getOrNull()
        if (session == null) {
            state = RouteUiState.LoginRequired()
            return
        }
        onAuthenticated(session.userName, session.hasRouteAndDeliveryAccess())
    }

    fun login(email: String, password: String) = loginWithCredentials(email, password)

    /**
     * Same outcome as [login], but with two extra hooks the login screen's biometric flow needs
     * and this controller (and [RouteUiState]) otherwise has no reason to know about: [onSuccess]
     * (worth remembering these credentials?) and [onInvalidCredentials] (a rejected password —
     * 401 — means previously-remembered credentials are stale and worth forgetting; any other
     * failure, network down included, does not).
     */
    fun loginWithCredentials(
        email: String,
        password: String,
        onSuccess: () -> Unit = {},
        onInvalidCredentials: () -> Unit = {},
    ) {
        state = RouteUiState.LoginRequired(busy = true)
        scope.launch {
            container.loginUseCase(email, password).fold(
                onSuccess = { session ->
                    onSuccess()
                    onAuthenticated(session.userName, session.hasRouteAndDeliveryAccess())
                },
                onFailure = { err ->
                    if (container.loginUseCase.isInvalidCredentials(err)) onInvalidCredentials()
                    state = RouteUiState.LoginRequired(error = err.message ?: "Falha no login")
                },
            )
        }
    }

    private suspend fun onAuthenticated(name: String, hasAccess: Boolean) {
        userName = name
        if (!hasAccess) {
            state = RouteUiState.NoAccess(name)
            return
        }
        loadVehicles()
    }

    private suspend fun loadVehicles() {
        state = RouteUiState.VehiclePicker(vehicles = emptyList(), userName = userName, busy = true)
        container.listVehiclesUseCase().fold(
            onSuccess = { vehicles -> state = RouteUiState.VehiclePicker(vehicles, userName) },
            onFailure = { err -> state = RouteUiState.VehiclePicker(emptyList(), userName, error = err.message) },
        )
    }

    fun selectVehicle(vehicle: Vehicle) {
        val current = state as? RouteUiState.VehiclePicker
        if (current != null) state = current.copy(busy = true, error = null)
        scope.launch {
            container.loadTodaysRouteUseCase(vehicle.id).fold(
                onSuccess = { plan -> applyPlan(vehicle, plan) },
                onFailure = { err ->
                    state = RouteUiState.VehiclePicker(current?.vehicles ?: emptyList(), userName, error = err.message)
                },
            )
        }
    }

    /** Back to vehicle picker — e.g. the driver is taking a different vehicle today. */
    fun changeVehicle() {
        scope.launch { loadVehicles() }
    }

    fun refresh() {
        when (val s = state) {
            is RouteUiState.ActiveRoute -> refreshPlan(s.vehicle, s.plan.id)
            is RouteUiState.RouteOptions -> refreshPlan(s.vehicle, s.plan.id)
            is RouteUiState.NoRouteToday -> selectVehicle(s.vehicle)
            is RouteUiState.RouteCompleted -> refreshPlan(s.vehicle, s.plan.id)
            else -> {}
        }
    }

    private fun refreshPlan(vehicle: Vehicle, planId: String) {
        scope.launch {
            container.refreshRouteUseCase(planId).fold(
                onSuccess = { plan -> applyPlan(vehicle, plan) },
                onFailure = { err -> state = RouteUiState.Error(err.message ?: "Erro ao atualizar a rota") },
            )
        }
    }

    fun selectOption(option: RouteOption) {
        val current = state as? RouteUiState.RouteOptions ?: return
        state = current.copy(busy = true)
        scope.launch {
            container.selectRouteOptionUseCase(current.plan.id, option).fold(
                onSuccess = { plan -> applyPlan(current.vehicle, plan) },
                onFailure = { state = current.copy(busy = false) },
            )
        }
    }

    fun confirmArrival() {
        val current = state as? RouteUiState.ActiveRoute ?: return
        val stop = current.plan.stops.firstOrNull() ?: return
        state = current.copy(busy = true, error = null)
        scope.launch {
            container.confirmArrivalUseCase(stop.salesOrderId).fold(
                onSuccess = { refreshPlan(current.vehicle, current.plan.id) },
                onFailure = { err -> state = current.copy(busy = false, error = err.message ?: "Falha ao confirmar chegada") },
            )
        }
    }

    fun failDelivery(note: String) {
        val current = state as? RouteUiState.ActiveRoute ?: return
        val stop = current.plan.stops.firstOrNull() ?: return
        state = current.copy(busy = true, error = null)
        scope.launch {
            container.failDeliveryUseCase(stop.salesOrderId, note).fold(
                onSuccess = { refreshPlan(current.vehicle, current.plan.id) },
                onFailure = { err -> state = current.copy(busy = false, error = err.message ?: "Falha ao registrar a tentativa") },
            )
        }
    }

    fun logout() {
        container.logoutUseCase()
        userName = ""
        state = RouteUiState.LoginRequired()
    }

    private fun applyPlan(vehicle: Vehicle, plan: RoutePlan?) {
        state = when {
            plan == null -> RouteUiState.NoRouteToday(vehicle)
            plan.stops.isEmpty() -> RouteUiState.RouteCompleted(vehicle, plan)
            plan.hasAlternatives -> RouteUiState.RouteOptions(vehicle, plan)
            else -> RouteUiState.ActiveRoute(vehicle, plan)
        }
    }
}
