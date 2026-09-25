package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.domain.repository.ConfigRepository

class ListVehiclesUseCase(private val repository: ConfigRepository) {
    suspend operator fun invoke(): Result<List<Vehicle>> =
        repository.listVehicles().map { list -> list.filter { it.active } }
}
