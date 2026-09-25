package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.repository.RouteRepository

class ConfirmArrivalUseCase(private val repository: RouteRepository) {
    suspend operator fun invoke(salesOrderId: String): Result<Unit> = repository.confirmArrival(salesOrderId)
}
