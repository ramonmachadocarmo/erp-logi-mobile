package com.example.myapplication.shared.domain.model

data class Address(
    val street: String = "",
    val number: String = "",
    val complement: String = "",
    val district: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
) {
    /**
     * Same field order/format as apps/web/packages/shared/src/maps.ts's addressQuery — a stop's
     * address reads identically here, in the web route report, and (if ever needed) in a
     * Maps/Waze handoff. Deliberately NOT used to look up coordinates: the in-app map draws from
     * the [Stop.lat]/[Stop.lng] the delivery plan already resolved server-side (sales-service's
     * OSRM geocode), not from a second, independent text lookup.
     */
    fun formatted(): String {
        val line1 = listOf(street, number).filter { it.isNotBlank() }.joinToString(", ")
        val cityState = when {
            city.isNotBlank() && state.isNotBlank() -> "$city - $state"
            else -> city.ifBlank { state }
        }
        return listOf(line1, district, cityState, zip).filter { it.isNotBlank() }.joinToString(", ")
    }
}
