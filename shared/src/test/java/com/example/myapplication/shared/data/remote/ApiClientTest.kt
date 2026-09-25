package com.example.myapplication.shared.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String? = "abc123", onUnauthorized: () -> Unit = {}) =
        ApiClient(
            tokenProvider = { token },
            onUnauthorized = onUnauthorized,
            explicitBaseUrl = server.url("/").toString().trimEnd('/'),
        )

    @Test
    fun `attaches the bearer token on every request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client().get("/api/sales/delivery-plans/x")
        val recorded = server.takeRequest()
        assertEquals("Bearer abc123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `login path does not trigger onUnauthorized on 401`() = runTest {
        var called = false
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid credentials"}"""))
        val c = client(token = null, onUnauthorized = { called = true })
        try {
            c.post("/api/identity/auth/login", JSONObject().put("email", "a@b.com").put("password", "123456"))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.statusCode)
        }
        assertTrue("onUnauthorized must NOT fire for the login endpoint itself" , !called)
    }

    @Test
    fun `a 401 outside login triggers onUnauthorized`() = runTest {
        var called = false
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"expired"}"""))
        val c = client(onUnauthorized = { called = true })
        try {
            c.get("/api/sales/delivery-plans")
            fail("expected ApiException")
        } catch (_: ApiException) {
        }
        assertTrue(called)
    }

    @Test
    fun `error message comes from the response body's error field`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"pedido ja confirmado"}"""))
        try {
            client().post("/api/sales/delivery-plans/p1/confirm")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("pedido ja confirmado", e.message)
            assertEquals(409, e.statusCode)
        }
    }

    @Test
    fun `204 No Content resolves to an empty object, not an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = client().post("/api/sales/sales-orders/o1/deliver")
        assertEquals(0, result.length())
    }
}
