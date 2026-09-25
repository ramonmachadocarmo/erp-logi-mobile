package com.example.myapplication.shared.data.sync

import com.example.myapplication.shared.data.cache.FilePendingActionQueue
import com.example.myapplication.shared.data.remote.ApiException
import com.example.myapplication.shared.domain.model.PendingAction
import com.example.myapplication.shared.domain.model.PendingActionType
import com.example.myapplication.shared.domain.model.RouteOption
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.repository.RouteRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class RecordingRemote : RouteRepository {
    val calls = mutableListOf<String>()
    /** order id -> failure to return; absent = success. */
    val failures = mutableMapOf<String, Throwable>()

    private fun run(call: String, order: String): Result<Unit> {
        calls += "$call:$order"
        return failures[order]?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun confirmArrival(salesOrderId: String) = run("deliver", salesOrderId)
    override suspend fun failDelivery(salesOrderId: String, note: String) = run("fail($note)", salesOrderId)
    override suspend fun loadTodaysPlan(vehicleId: String): Result<RoutePlan?> = error("unused")
    override suspend fun refreshPlan(planId: String): Result<RoutePlan?> = error("unused")
    override suspend fun selectOption(planId: String, option: RouteOption): Result<RoutePlan> = error("unused")
}

class PendingActionSyncerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var queue: FilePendingActionQueue
    private lateinit var remote: RecordingRemote
    private lateinit var syncer: PendingActionSyncer

    private fun add(id: String, order: String, at: Long, type: PendingActionType = PendingActionType.DELIVER, note: String = "") =
        queue.enqueue(PendingAction(id, type, order, note, createdAtMillis = at))

    @Before
    fun setUp() {
        queue = FilePendingActionQueue(File(tmp.root, "q.json"))
        remote = RecordingRemote()
        syncer = PendingActionSyncer(queue, remote)
    }

    @Test
    fun `empty queue is a no-op`() = runTest {
        assertEquals(SyncOutcome(), syncer.sync())
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun `sends everything oldest first and empties the queue`() = runTest {
        add("b", "o2", at = 20L, type = PendingActionType.FAIL, note = "ausente")
        add("a", "o1", at = 10L)

        val out = syncer.sync()

        assertEquals(listOf("deliver:o1", "fail(ausente):o2"), remote.calls)
        assertEquals(SyncOutcome(sent = 2), out)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `no network stops at once, keeps the action and asks for a retry`() = runTest {
        add("a", "o1", 1L)
        add("b", "o2", 2L)
        remote.failures["o1"] = ApiException(null, "sem rede", isNetwork = true)

        val out = syncer.sync()

        assertEquals(listOf("deliver:o1"), remote.calls) // o2 not even attempted
        assertTrue(out.retryLater)
        assertEquals(listOf("a", "b"), queue.pending().map { it.id })
        assertEquals(1, queue.pending()[0].attempts)
    }

    @Test
    fun `a sent action is removed even if a later one hits the network wall`() = runTest {
        add("a", "o1", 1L)
        add("b", "o2", 2L)
        remote.failures["o2"] = ApiException(null, "sem rede", isNetwork = true)

        val out = syncer.sync()

        assertEquals(1, out.sent)
        assertEquals(listOf("b"), queue.pending().map { it.id })
    }

    @Test
    fun `401 keeps everything and asks for login instead of retrying`() = runTest {
        add("a", "o1", 1L)
        remote.failures["o1"] = ApiException(401, "unauthorized")

        val out = syncer.sync()

        assertTrue(out.needsLogin)
        assertFalse(out.retryLater)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `5xx is retried later but does not block the other actions`() = runTest {
        add("a", "o1", 1L)
        add("b", "o2", 2L)
        remote.failures["o1"] = ApiException(503, "indisponivel")

        val out = syncer.sync()

        assertEquals(listOf("deliver:o1", "deliver:o2"), remote.calls)
        assertEquals(SyncOutcome(sent = 1, retryLater = true), out)
        assertEquals(listOf("a"), queue.pending().map { it.id })
        assertEquals("indisponivel", queue.pending()[0].lastError)
    }

    @Test
    fun `408 and 429 count as transient`() = runTest {
        add("a", "o1", 1L)
        add("b", "o2", 2L)
        remote.failures["o1"] = ApiException(408, "timeout")
        remote.failures["o2"] = ApiException(429, "slow down")
        val out = syncer.sync()
        assertTrue(out.retryLater)
        assertEquals(0, out.rejected)
        assertEquals(2, queue.pending().size)
    }

    @Test
    fun `a permanent 4xx is marked rejected, never retried, and does not block others`() = runTest {
        add("a", "o1", 1L)
        add("b", "o2", 2L)
        remote.failures["o1"] = ApiException(409, "pedido ja entregue")

        val out = syncer.sync()

        assertEquals(SyncOutcome(sent = 1, rejected = 1), out)
        assertTrue(queue.pending().isEmpty())
        assertEquals("pedido ja entregue", queue.rejected().single().rejectedReason)

        remote.calls.clear()
        assertEquals(SyncOutcome(), syncer.sync())
        assertTrue(remote.calls.isEmpty()) // rejected one is not resent
    }

    @Test
    fun `unexpected exceptions are counted and retried`() = runTest {
        add("a", "o1", 1L)
        remote.failures["o1"] = IllegalStateException("bug")
        val out = syncer.sync()
        assertTrue(out.retryLater)
        assertEquals("bug", queue.pending().single().lastError)
    }

    @Test
    fun `second pass after connectivity returns delivers what was left`() = runTest {
        add("a", "o1", 1L)
        remote.failures["o1"] = ApiException(null, "sem rede", isNetwork = true)
        assertTrue(syncer.sync().retryLater)

        remote.failures.clear()
        assertEquals(SyncOutcome(sent = 1), syncer.sync())
        assertTrue(queue.pending().isEmpty())
    }
}
