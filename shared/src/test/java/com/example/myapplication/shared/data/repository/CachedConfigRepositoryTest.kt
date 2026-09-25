package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.cache.FileOfflineCache
import com.example.myapplication.shared.data.remote.ApiException
import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.domain.repository.ConfigRepository
import com.example.myapplication.shared.vehicle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CachedConfigRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var vehicles: Result<List<Vehicle>> = Result.success(emptyList())
    private val delegate = object : ConfigRepository {
        override suspend fun listVehicles() = vehicles
        override suspend fun customerNames() = Result.success(mapOf("c1" to "Um"))
    }
    private lateinit var repo: CachedConfigRepository

    @Before
    fun setUp() {
        repo = CachedConfigRepository(delegate, FileOfflineCache(File(tmp.root, "offline")))
    }

    @Test
    fun `online result is returned and remembered for offline`() = runTest {
        vehicles = Result.success(listOf(vehicle("v1")))
        assertEquals(listOf(vehicle("v1")), repo.listVehicles().getOrThrow())

        vehicles = Result.failure(ApiException(null, "sem rede", isNetwork = true))
        assertEquals(listOf(vehicle("v1")), repo.listVehicles().getOrThrow())
    }

    @Test
    fun `offline with nothing cached keeps the network error`() = runTest {
        vehicles = Result.failure(ApiException(null, "sem rede", isNetwork = true))
        assertTrue((repo.listVehicles().exceptionOrNull() as ApiException).isNetwork)
    }

    @Test
    fun `server errors are not masked`() = runTest {
        vehicles = Result.success(listOf(vehicle("v1")))
        repo.listVehicles()
        vehicles = Result.failure(ApiException(403, "forbidden"))
        assertEquals(403, (repo.listVehicles().exceptionOrNull() as ApiException).statusCode)
    }

    @Test
    fun `customerNames passes straight through`() = runTest {
        assertEquals(mapOf("c1" to "Um"), repo.customerNames().getOrThrow())
    }
}
