package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.remote.ApiException
import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.domain.repository.ConfigRepository
import com.example.myapplication.shared.domain.repository.VehicleCache

/** Same idea as [CachedRouteRepository]: the vehicle picker is the gate to the cached route offline. */
class CachedConfigRepository(
    private val delegate: ConfigRepository,
    private val cache: VehicleCache,
) : ConfigRepository {

    override suspend fun listVehicles(): Result<List<Vehicle>> {
        val result = delegate.listVehicles()
        result.onSuccess { cache.save(it) }
        val err = result.exceptionOrNull() as? ApiException ?: return result
        if (!err.isNetwork) return result
        return cache.read()?.let { Result.success(it) } ?: result
    }

    override suspend fun customerNames(): Result<Map<String, String>> = delegate.customerNames()
}
