package com.example.myapplication.shared.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressTest {

    @Test
    fun `formats a full address like the web's addressQuery`() {
        val a = Address(
            street = "Rua das Flores", number = "123", district = "Centro",
            city = "Manaus", state = "AM", zip = "69000-000",
        )
        assertEquals("Rua das Flores, 123, Centro, Manaus - AM, 69000-000", a.formatted())
    }

    @Test
    fun `blank fields are dropped, not left as stray separators`() {
        val a = Address(street = "Av. Djalma Batista", city = "Manaus")
        assertEquals("Av. Djalma Batista, Manaus", a.formatted())
    }

    @Test
    fun `empty address formats to an empty string`() {
        assertEquals("", Address().formatted())
    }

    @Test
    fun `state alone without city still renders`() {
        val a = Address(street = "Rua X", state = "AM")
        assertEquals("Rua X, AM", a.formatted())
    }
}
