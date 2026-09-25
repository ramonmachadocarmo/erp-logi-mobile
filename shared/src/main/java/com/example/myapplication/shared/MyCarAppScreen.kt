package com.example.myapplication.shared

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.presentation.RouteFlowController
import com.example.myapplication.shared.presentation.RouteUiState

/**
 * Android Auto never shows a credential form — typing an email/password on the car screen is
 * both awkward with the car-template widgets and discouraged for driver safety. Login happens on
 * the phone (MainActivity); this screen just reads the session AppContainer/TokenStore already
 * has (same encrypted storage, shared via :shared) and asks the driver to use the phone if there
 * isn't one yet. Picking a vehicle and confirming a stop are plain list-row taps, which the
 * car-template system is built for, so those stay here.
 */
class MyCarAppScreen(carContext: CarContext) : Screen(carContext) {

    private val container = AppContainer(carContext)
    private var onRouteUpdatedListener: ((RoutePlan?) -> Unit)? = null

    private val controller = RouteFlowController(container, lifecycleScope) { state ->
        onRouteUpdatedListener?.invoke(activePlanOf(state))
        invalidate()
    }

    init {
        controller.start()
    }

    fun setOnRouteUpdatedListener(listener: (RoutePlan?) -> Unit) {
        onRouteUpdatedListener = listener
    }

    fun currentPlan(): RoutePlan? = activePlanOf(controller.state)

    private fun activePlanOf(state: RouteUiState): RoutePlan? = when (state) {
        is RouteUiState.ActiveRoute -> state.plan
        is RouteUiState.RouteOptions -> state.plan
        is RouteUiState.RouteCompleted -> state.plan
        else -> null
    }

    override fun onGetTemplate(): Template =
        when (val state = controller.state) {
            is RouteUiState.Restoring -> messageTemplate("Carregando...")
            is RouteUiState.LoginRequired -> loginRequiredTemplate()
            is RouteUiState.NoAccess -> messageTemplate("${state.userName} não tem acesso a Rotas e Entregas.")
            is RouteUiState.VehiclePicker -> vehiclePickerTemplate(state)
            is RouteUiState.NoRouteToday -> noRouteTemplate(state)
            is RouteUiState.RouteOptions -> routeOptionsTemplate(state)
            is RouteUiState.ActiveRoute -> activeRouteTemplate(state)
            is RouteUiState.RouteCompleted -> completedTemplate(state)
            is RouteUiState.Error -> messageTemplate(state.message)
        }

    private fun messageTemplate(text: String): MessageTemplate =
        MessageTemplate.Builder(text)
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Rotas")
            .build()

    private fun loginRequiredTemplate(): MessageTemplate =
        MessageTemplate.Builder("Faça login no app no celular para carregar sua rota.")
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Login necessário")
            .addAction(
                Action.Builder()
                    .setTitle("Tentar novamente")
                    .setOnClickListener { controller.start() }
                    .build(),
            )
            .build()

    private fun vehiclePickerTemplate(state: RouteUiState.VehiclePicker): Template {
        if (state.vehicles.isEmpty() && !state.busy) {
            return MessageTemplate.Builder(state.error ?: "Nenhum veículo cadastrado.")
                .setHeaderAction(Action.APP_ICON)
                .setTitle("Veículos")
                .addAction(
                    Action.Builder()
                        .setTitle("Tentar novamente")
                        .setOnClickListener { controller.changeVehicle() }
                        .build(),
                )
                .build()
        }
        val items = ItemList.Builder()
        state.vehicles.forEach { vehicle ->
            items.addItem(
                Row.Builder()
                    .setTitle(vehicle.label)
                    .setOnClickListener { controller.selectVehicle(vehicle) }
                    .build(),
            )
        }
        return ListTemplate.Builder()
            .setTitle("Olá, ${state.userName} — escolha o veículo")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .build()
    }

    private fun noRouteTemplate(state: RouteUiState.NoRouteToday): MessageTemplate =
        MessageTemplate.Builder("Nenhuma rota planejada hoje para ${state.vehicle.label}.")
            .setHeaderAction(Action.BACK)
            .setTitle("Rota")
            .addAction(
                Action.Builder()
                    .setTitle("Atualizar")
                    .setOnClickListener { controller.refresh() }
                    .build(),
            )
            .build()

    private fun routeOptionsTemplate(state: RouteUiState.RouteOptions): ListTemplate {
        val items = ItemList.Builder()
        state.plan.options.forEach { option ->
            items.addItem(
                Row.Builder()
                    .setTitle(option.label)
                    .addText("${(option.durationS / 60).toInt()} min  •  ${"%.1f".format(option.distanceM / 1000)} km  •  ${option.stops.size} paradas")
                    .setOnClickListener { controller.selectOption(option) }
                    .build(),
            )
        }
        return ListTemplate.Builder()
            .setTitle("Escolha a rota • ${state.vehicle.label}")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }

    private fun activeRouteTemplate(state: RouteUiState.ActiveRoute): PlaceListMapTemplate {
        val plan = state.plan
        val items = ItemList.Builder()
        val current = plan.stops.firstOrNull()

        plan.stops.forEachIndexed { index, stop ->
            val place = Place.Builder(CarLocation.create(stop.lat, stop.lng)).build()
            val status = if (index == 0) "🎯 [Toque para confirmar chegada]" else "⏳ [A caminho]"
            val rowBuilder = Row.Builder()
                .setTitle("${index + 1}. ${stop.customerName} $status")
                .addText(stop.address.formatted())
                .setMetadata(Metadata.Builder().setPlace(place).build())
            if (index == 0) {
                rowBuilder.setOnClickListener { controller.confirmArrival() }
            }
            items.addItem(rowBuilder.build())
        }

        val actionStripBuilder = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("✅ Confirmar chegada")
                    .setOnClickListener { controller.confirmArrival() }
                    .build(),
            )
        if (plan.options.size > 1) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle("🔄 Trocar rota")
                    .setOnClickListener { controller.refresh() }
                    .build(),
            )
        }

        val title = if (current != null) {
            "${current.customerName} (${plan.totalTimeMinutes} min restantes)"
        } else {
            "${plan.vehicleName} (${plan.totalTimeMinutes} min • ${"%.1f".format(plan.totalDistanceKm)} km)"
        }

        val templateBuilder = PlaceListMapTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStripBuilder.build())
            .setItemList(items.build())

        if (current != null) {
            templateBuilder.setAnchor(Place.Builder(CarLocation.create(current.lat, current.lng)).build())
        }
        return templateBuilder.build()
    }

    private fun completedTemplate(state: RouteUiState.RouteCompleted): MessageTemplate =
        MessageTemplate.Builder("🎉 Rota concluída!\nTodas as entregas de ${state.vehicle.label} foram finalizadas.")
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Rota")
            .addAction(
                Action.Builder()
                    .setTitle("🔄 Trocar de veículo")
                    .setOnClickListener { controller.changeVehicle() }
                    .build(),
            )
            .build()

    companion object {
        val colorLightGreen: CarColor = CarColor.createCustom(0xFF81C784.toInt(), 0xFF388E3C.toInt())
        val colorOrange: CarColor = CarColor.createCustom(0xFFFF9800.toInt(), 0xFFE65100.toInt())
    }
}
