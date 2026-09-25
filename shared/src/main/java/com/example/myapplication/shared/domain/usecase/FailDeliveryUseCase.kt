package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.repository.RouteRepository

class FailDeliveryUseCase(private val repository: RouteRepository) {
    suspend operator fun invoke(salesOrderId: String, note: String): Result<Unit> =
        repository.failDelivery(salesOrderId, note)
}
