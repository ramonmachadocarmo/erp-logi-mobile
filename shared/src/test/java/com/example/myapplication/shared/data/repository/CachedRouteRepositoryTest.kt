package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.cache.FileOfflineCache
import com.example.myapplication.shared.data.cache.FilePendingActionQueue
import com.example.myapplication.shared.data.remote.ApiException
import com.example.myapplication.shared.domain.model.PendingAction
import com.example.myapplication.shared.domain.model.PendingActionType
import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.repository.RouteRepository
import com.example.myapplication.shared.plan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeRouteRepository : RouteRepository {
    var loadResult: Result<RoutePlan?> = Result.success(null)
    var refreshResult: Result<RoutePlan?> = Result.success(null)
    var selectResult: Result<RoutePlan> = Result.failure(IllegalStateException("unset"))
    var confirmResult: Result<Unit> = Result.success(Unit)
    var failResult: Result<Unit> = Result.success(Unit)

    override suspend fun loadTodaysPlan(vehicleId: String) = loadResult
    override suspend fun refreshPlan(planId: String) = refreshResult
    override suspend fun selectOption(planId: String, option: RouteOption) = selectResult
    override suspend fun confirmArrival(salesOrderId: String) = confirmResult
    override suspend fun failDelivery(salesOrderId: String, note: String) = failResult
}

class CachedRouteRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var delegate: FakeRouteRepository
    private lateinit var cache: FileOfflineCache
    private lateinit var queue: FilePendingActionQueue
    private var queuedSignals = 0
    private var ids = 0
    private lateinit var repo: CachedRouteRepository

    private val offline = Result.failure<Nothing>(ApiException(null, "sem rede", isNetwork = true))
    private val today = "2026-09-24"
    private val anyOption = RouteOption("x", 1.0, 1.0, emptyList(), emptyList())

    @Before
    fun setUp() {
        delegate = FakeRouteRepository()
        cache = FileOfflineCache(File(tmp.root, "offline"))
        queue = FilePendingActionQueue(File(tmp.root, "pending/actions.json"))
        repo = CachedRouteRepository(
            delegate, cache, queue,
            onQueued = { queuedSignals++ },
            today = { today }, clock = { 999L }, newId = { "id${ids++}" },
        )
    }

    @Test
    fun `online load returns the fresh plan untouched and caches it`() = runTest {
        val fresh = plan()
        delegate.loadResult = Result.success(fresh)

        val got = repo.loadTodaysPlan("veh1").getOrThrow()!!

        assertEquals(fresh, got)
        assertFalse(got.isFromCache)
        assertEquals(fresh.copy(cachedAtMillis = 999L), cache.findByVehicle("veh1", today))
    }

    @Test
    fun `offline load falls back to the cached plan flagged with when it was saved`() = runTest {
        delegate.loadResult = Result.success(plan())
        repo.loadTodaysPlan("veh1")

        delegate.loadResult = offline
        val got = repo.loadTodaysPlan("veh1").getOrThrow()!!

        assertTrue(got.isFromCache)
        assertEquals(999L, got.cachedAtMillis)
        assertEquals(2, got.stops.size)
    }

    @Test
    fun `offline load with nothing cached keeps the network error`() = runTest {
        delegate.loadResult = offline
        val err = repo.loadTodaysPlan("veh1").exceptionOrNull() as ApiException
        assertTrue(err.isNetwork)
    }

    @Test
    fun `offline load ignores a plan cached on a previous day`() = runTest {
        cache.save("2026-09-23", plan(), 1L)
        delegate.loadResult = offline
        assertTrue(repo.loadTodaysPlan("veh1").isFailure)
    }

    @Test
    fun `server errors are never masked by the cache`() = runTest {
        delegate.loadResult = Result.success(plan())
        repo.loadTodaysPlan("veh1")

        delegate.loadResult = Result.failure(ApiException(500, "boom", isNetwork = false))
        assertEquals(500, (repo.loadTodaysPlan("veh1").exceptionOrNull() as ApiException).statusCode)

        delegate.loadResult = Result.failure(ApiException(401, "unauthorized"))
        assertEquals(401, (repo.loadTodaysPlan("veh1").exceptionOrNull() as ApiException).statusCode)
    }

    @Test
    fun `non-API failures are not masked either`() = runTest {
        delegate.loadResult = Result.success(plan())
        repo.loadTodaysPlan("veh1")
        delegate.loadResult = Result.failure(IllegalStateException("parse"))
        assertTrue(repo.loadTodaysPlan("veh1").exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `server saying nothing is planned evicts the stale cached plan`() = runTest {
        delegate.loadResult = Result.success(plan())
        repo.loadTodaysPlan("veh1")

        delegate.loadResult = Result.success(null)
        assertNull(repo.loadTodaysPlan("veh1").getOrThrow())
        assertNull(cache.findByVehicle("veh1", today))
    }

    @Test
    fun `refresh caches on success and falls back by plan id when offline`() = runTest {
        delegate.refreshResult = Result.success(plan(id = "planX"))
        repo.refreshPlan("planX")

        delegate.refreshResult = offline
        val got = repo.refreshPlan("planX").getOrThrow()!!
        assertEquals("planX", got.id)
        assertTrue(got.isFromCache)
    }

    @Test
    fun `refresh offline for an unknown plan id fails`() = runTest {
        delegate.refreshResult = offline
        assertTrue(repo.refreshPlan("unknown").isFailure)
    }

    @Test
    fun `selectOption caches the confirmed plan`() = runTest {
        delegate.selectResult = Result.success(plan(id = "p9"))
        repo.selectOption("p9", anyOption)
        assertEquals("p9", cache.findByPlanId("p9", today)!!.id)
    }

    @Test
    fun `a failed selectOption does not touch the cache`() = runTest {
        cache.save(today, plan(id = "old"), 1L)
        delegate.selectResult = offline
        assertTrue(repo.selectOption("old", anyOption).isFailure)
        assertEquals("old", cache.findByVehicle("veh1", today)!!.id)
    }

    @Test
    fun `confirmArrival online removes the stop from the cache and queues nothing`() = runTest {
        cache.save(today, plan(), 1L)
        assertTrue(repo.confirmArrival("o1").isSuccess)
        assertEquals(listOf("o2"), cache.findByVehicle("veh1", today)!!.stops.map { it.salesOrderId })
        assertTrue(queue.pending().isEmpty())
        assertEquals(0, queuedSignals)
    }

    @Test
    fun `confirmArrival offline is queued, reported as success and the stop disappears`() = runTest {
        delegate.loadResult = Result.success(plan())
        repo.loadTodaysPlan("veh1")

        delegate.confirmResult = offline
        assertTrue(repo.confirmArrival("o1").isSuccess)

        val queued = queue.pending().single()
        assertEquals(PendingActionType.DELIVER, queued.type)
        assertEquals("o1", queued.salesOrderId)
        assertEquals(1, queuedSignals)

        delegate.refreshResult = offline
        val got = repo.refreshPlan("plan1").getOrThrow()!!
        assertEquals(listOf("o2"), got.stops.map { it.salesOrderId })
        assertEquals(1, got.pendingSyncCount)
        assertTrue(got.isFromCache)
    }

    @Test
    fun `back online but not yet synced, the server's stale stop stays hidden`() = runTest {
        delegate.confirmResult = offline
        repo.confirmArrival("o1")

        delegate.loadResult = Result.success(plan()) // server still lists o1 as undelivered
        val got = repo.loadTodaysPlan("veh1").getOrThrow()!!
        assertEquals(listOf("o2"), got.stops.map { it.salesOrderId })
        assertEquals(1, got.pendingSyncCount)
        assertFalse(got.isFromCache)
    }

    @Test
    fun `confirming the same order twice offline queues it once`() = runTest {
        delegate.confirmResult = offline
        repo.confirmArrival("o1")
        assertTrue(repo.confirmArrival("o1").isSuccess)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `server errors on confirmArrival are surfaced, not queued`() = runTest {
        delegate.confirmResult = Result.failure(ApiException(409, "ja entregue"))
        assertEquals(409, (repo.confirmArrival("o1").exceptionOrNull() as ApiException).statusCode)
        delegate.confirmResult = Result.failure(ApiException(500, "boom"))
        assertTrue(repo.confirmArrival("o1").isFailure)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `failDelivery offline is queued with its note and the stop stays listed`() = runTest {
        cache.save(today, plan(), 1L)
        delegate.failResult = offline
        assertTrue(repo.failDelivery("o1", "cliente ausente").isSuccess)

        val queued = queue.pending().single()
        assertEquals(PendingActionType.FAIL, queued.type)
        assertEquals("cliente ausente", queued.note)
        assertEquals(2, cache.findByVehicle("veh1", today)!!.stops.size)
    }

    @Test
    fun `failDelivery online passes through and server errors are not queued`() = runTest {
        assertTrue(repo.failDelivery("o1", "x").isSuccess)
        delegate.failResult = Result.failure(ApiException(500, "boom"))
        assertTrue(repo.failDelivery("o1", "x").isFailure)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `rejected actions are reported on the plan`() = runTest {
        queue.enqueue(PendingAction("r1", PendingActionType.DELIVER, "o9", createdAtMillis = 1L))
        queue.markRejected("r1", "ja entregue")
        delegate.loadResult = Result.success(plan())
        val got = repo.loadTodaysPlan("veh1").getOrThrow()!!
        assertEquals(1, got.rejectedSyncCount)
        assertEquals(0, got.pendingSyncCount)
    }
}
