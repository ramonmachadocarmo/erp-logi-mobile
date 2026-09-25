package com.example.myapplication.shared.domain.model

/**
 * Mirrors apps/web/packages/shared/src/permissions.ts: PERM_NONE/PERM_VIEW/PERM_EDIT, MASTER role
 * bypass, and menu_permissions keyed by the same menu paths the web/mobile role matrix use.
 * This app only ever needs to know "can I see it at all" (PERM_VIEW+), never edit-level.
 */
data class Session(
    val token: String,
    val userId: String,
    val userName: String,
    val roleCode: String,
    val menuPermissions: Map<String, Int>,
) {
    private fun level(menuKey: String): Int =
        if (roleCode == "MASTER") PERM_EDIT else menuPermissions[menuKey] ?: PERM_NONE

    /** Login is only granted to roles that can see BOTH Rotas and Entrega on the web/mobile menu. */
    fun hasRouteAndDeliveryAccess(): Boolean =
        level(MENU_KEY_ROTAS) > PERM_NONE && level(MENU_KEY_ENTREGA) > PERM_NONE

    companion object {
        const val PERM_NONE = 0
        const val PERM_VIEW = 1
        const val PERM_EDIT = 2
        const val MENU_KEY_ROTAS = "/logistica/rotas"
        const val MENU_KEY_ENTREGA = "/logistica/entrega"
    }
}
