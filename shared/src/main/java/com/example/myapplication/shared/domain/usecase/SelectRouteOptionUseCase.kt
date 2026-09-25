package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.repository.RouteRepository

/** Replaces the old OptimizeRouteWithTrafficUseCase: "recalcular" is now picking one of the
 *  plan's real, server-computed alternatives (see RouteOption) instead of running a fake
 *  on-device heuristic. */
class SelectRouteOptionUseCase(private val repository: RouteRepository) {
    suspend operator fun invoke(planId: String, option: RouteOption): Result<RoutePlan> =
        repository.selectOption(planId, option)
}
