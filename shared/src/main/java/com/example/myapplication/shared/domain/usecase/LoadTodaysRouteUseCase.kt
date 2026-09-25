package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.repository.RouteRepository

class LoadTodaysRouteUseCase(private val repository: RouteRepository) {
    suspend operator fun invoke(vehicleId: String): Result<RoutePlan?> =
        repository.loadTodaysPlan(vehicleId)
}
