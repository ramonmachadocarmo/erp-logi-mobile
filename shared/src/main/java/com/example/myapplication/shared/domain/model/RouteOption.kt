package com.example.myapplication.shared.domain.model

/**
 * One route alternative computed server-side (sales-service's CreatePlans, via OSRM) — usually up
 * to 3 per plan, labeled "Melhor tempo" / "Menor distância" / "Alternativa". Replaces the old
 * SimulatedTrafficOptimizerEngine's fake reversed-stops "optimization": recalculating a route now
 * means picking one of these real options and confirming it (RouteRepository.selectOption), not
 * running any on-device heuristic.
 */
data class RouteOption(
    val label: String,
    val distanceM: Double,
    val durationS: Double,
    val geometry: List<List<Double>>,
    val stops: List<Stop>,
    val selected: Boolean = false,
    /**
     * The option exactly as sales-service sent it, kept only so RouteRepositoryImpl can POST it
     * back unchanged to /delivery-plans/:id/confirm — ConfirmPlan persists the whole RouteOption
     * server-side (weight_kg/volume_m3 per stop included), and this app's [Stop] model doesn't
     * carry every field, so round-tripping the original JSON avoids silently dropping any of it.
     * Not for display.
     */
    val rawJson: String = "",
)
