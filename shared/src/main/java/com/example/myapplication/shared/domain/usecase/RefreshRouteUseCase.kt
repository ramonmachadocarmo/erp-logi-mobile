package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.repository.RouteRepository

class RefreshRouteUseCase(private val repository: RouteRepository) {
    suspend operator fun invoke(planId: String): Result<RoutePlan?> = repository.refreshPlan(planId)
}
