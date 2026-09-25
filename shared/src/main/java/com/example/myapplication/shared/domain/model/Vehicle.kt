package com.example.myapplication.shared.domain.model

data class Vehicle(
    val id: String,
    val code: String,
    val name: String,
    val capacityKg: Double,
    val capacityM3: Double,
    val active: Boolean,
) {
    val label: String get() = if (code.isNotBlank()) "$code — $name" else name
}
