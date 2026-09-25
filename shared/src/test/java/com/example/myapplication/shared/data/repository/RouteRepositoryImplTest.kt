package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.remote.ApiClient
import com.example.myapplication.shared.domain.model.PlanStatus
import com.example.myapplication.shared.domain.repository.ConfigRepository
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val ONE_PLAN = """
{
  "id": "plan1", "vehicle_id": "veh1", "delivery_date": "2026-09-23", "status": "PLANNED",
  "distance_m": 15000, "duration_s": 2700, "vehicle_name": "Van", "vehicle_code": "V01",
  "center_name": "CD Manaus", "center_lat": -3.1, "center_lng": -60.0,
  "geometry": [[-3.1, -60.0], [-3.13, -60.02]],
  "stops": [
    {"seq": 1, "sales_order_id": "order-delivered", "customer_id": "c1", "distance_m": 1000, "duration_s": 200, "lat": -3.11, "lng": -60.01,
     "address": {"street": "Rua A", "city": "Manaus", "state": "AM"}},
    {"seq": 2, "sales_order_id": "order-pending", "customer_id": "c2", "distance_m": 2000, "duration_s": 400, "lat": -3.12, "lng": -60.02,
     "address": {"street": "Rua B", "city": "Manaus", "state": "AM"}}
  ],
  "options": []
}
"""

private val ORDERS = """
[
  {"id": "order-delivered", "status": "DELIVERED"},
  {"id": "order-pending", "status": "PICKED"}
]
"""

class RouteRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RouteRepositoryImpl

    private val fakeConfig = object : ConfigRepository {
        override suspend fun listVehicles() = Result.success(emptyList<com.example.myapplication.shared.domain.model.Vehicle>())
        override suspend fun customerNames() = Result.success(mapOf("c1" to "Cliente Um", "c2" to "Cliente Dois"))
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/sales/delivery-plans" -> MockResponse().setResponseCode(200).setBody("[$ONE_PLAN]")
                request.path?.startsWith("/api/sales/delivery-plans/plan1") == true -> MockResponse().setResponseCode(200).setBody(ONE_PLAN)
                request.path == "/api/sales/sales-orders" -> MockResponse().setResponseCode(200).setBody(ORDERS)
                request.path == "/api/sales/sales-orders/order-pending/deliver" -> MockResponse().setResponseCode(204)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val api = ApiClient(
            tokenProvider = { "token" },
            onUnauthorized = {},
            explicitBaseUrl = server.url("/").toString().trimEnd('/'),
        )
        // Fixed instead of LocalDate.now() — ONE_PLAN's delivery_date is a stub value, not really
        // "today"; a real clock would make this test flaky/date-dependent.
        repository = RouteRepositoryImpl(api, fakeConfig, today = { "2026-09-23" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `loadTodaysPlan drops stops whose order is already DELIVERED`() = runTest {
        val plan = repository.loadTodaysPlan("veh1").getOrThrow()!!
        assertEquals(1, plan.stops.size)
        assertEquals("order-pending", plan.stops[0].salesOrderId)
        assertEquals("Cliente Dois", plan.stops[0].customerName)
    }

    @Test
    fun `loadTodaysPlan resolves customer names via ConfigRepository`() = runTest {
        val plan = repository.loadTodaysPlan("veh1").getOrThrow()!!
        assertEquals("Cliente Dois", plan.stops.first().customerName)
    }

    @Test
    fun `loadTodaysPlan returns null when no plan matches the vehicle`() = runTest {
        val plan = repository.loadTodaysPlan("some-other-vehicle").getOrThrow()
        assertNull(plan)
    }

    @Test
    fun `geometry is parsed as lat-lng pairs in order`() = runTest {
        val plan = repository.loadTodaysPlan("veh1").getOrThrow()!!
        assertEquals(2, plan.geometry.size)
        assertEquals(-3.1, plan.geometry[0][0], 0.0001) // lat
        assertEquals(-60.0, plan.geometry[0][1], 0.0001) // lng
    }

    @Test
    fun `status and totals map straight from the DeliveryPlan JSON`() = runTest {
        val plan = repository.loadTodaysPlan("veh1").getOrThrow()!!
        assertEquals(PlanStatus.PLANNED, plan.status)
        assertEquals(15.0, plan.totalDistanceKm, 0.0001)
    }

    @Test
    fun `confirmArrival posts to sales-orders deliver with no body error`() = runTest {
        val result = repository.confirmArrival("order-pending")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `confirmArrival on an unknown order surfaces the 404 as a failure, not a crash`() = runTest {
        val result = repository.confirmArrival("does-not-exist")
        assertFalse(result.isSuccess)
    }
}
