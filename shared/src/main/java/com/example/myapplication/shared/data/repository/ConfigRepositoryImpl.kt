package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.remote.ApiClient
import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.domain.repository.ConfigRepository
import org.json.JSONObject

class ConfigRepositoryImpl(private val api: ApiClient) : ConfigRepository {

    override suspend fun listVehicles(): Result<List<Vehicle>> = runCatching {
        val arr = api.getList("/api/config/vehicles")
        (0 until arr.length()).map { i -> vehicleFrom(arr.getJSONObject(i)) }
    }

    override suspend fun customerNames(): Result<Map<String, String>> = runCatching {
        val arr = api.getList("/api/config/customers")
        val out = mutableMapOf<String, String>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val id = p.optString("id")
            if (id.isBlank()) continue
            val companyName = p.optString("company_name")
            val name = p.optString("name")
            out[id] = if (p.optString("kind") == "PJ" && companyName.isNotBlank()) companyName else name
        }
        out
    }

    private fun vehicleFrom(o: JSONObject) = Vehicle(
        id = o.optString("id"),
        code = o.optString("code"),
        name = o.optString("name"),
        capacityKg = o.optDouble("capacity_kg", 0.0),
        capacityM3 = o.optDouble("capacity_m3", 0.0),
        active = o.optBoolean("active", true),
    )
}
