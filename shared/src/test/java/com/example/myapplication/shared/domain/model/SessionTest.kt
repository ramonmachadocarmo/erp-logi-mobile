package com.example.myapplication.shared.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private fun session(roleCode: String, perms: Map<String, Int>) =
        Session(token = "t", userId = "u1", userName = "Driver", roleCode = roleCode, menuPermissions = perms)

    @Test
    fun `MASTER always has access regardless of menu_permissions`() {
        val s = session("MASTER", emptyMap())
        assertTrue(s.hasRouteAndDeliveryAccess())
    }

    @Test
    fun `needs view level on both rotas and entrega`() {
        val both = session("DRIVER", mapOf("/logistica/rotas" to 1, "/logistica/entrega" to 1))
        assertTrue(both.hasRouteAndDeliveryAccess())
    }

    @Test
    fun `missing entrega denies access even with rotas granted`() {
        val onlyRotas = session("DRIVER", mapOf("/logistica/rotas" to 2))
        assertFalse(onlyRotas.hasRouteAndDeliveryAccess())
    }

    @Test
    fun `missing rotas denies access even with entrega granted`() {
        val onlyEntrega = session("DRIVER", mapOf("/logistica/entrega" to 1))
        assertFalse(onlyEntrega.hasRouteAndDeliveryAccess())
    }

    @Test
    fun `PERM_NONE level counts as no access`() {
        val zeroed = session("DRIVER", mapOf("/logistica/rotas" to 0, "/logistica/entrega" to 0))
        assertFalse(zeroed.hasRouteAndDeliveryAccess())
    }

    @Test
    fun `no menu_permissions at all denies access for a non-MASTER role`() {
        assertFalse(session("DRIVER", emptyMap()).hasRouteAndDeliveryAccess())
    }
}
