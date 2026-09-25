package com.example.myapplication.shared.domain.repository

import com.example.myapplication.shared.domain.model.Vehicle

interface ConfigRepository {
    suspend fun listVehicles(): Result<List<Vehicle>>

    /** customer_id -> display name (company_name for PJ, name otherwise — mirrors the web's personName helper). */
    suspend fun customerNames(): Result<Map<String, String>>
}
