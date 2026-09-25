package com.example.myapplication.shared.data.cache

import com.example.myapplication.shared.domain.model.PendingAction
import com.example.myapplication.shared.domain.model.PendingActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilePendingActionQueueTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var file: File
    private lateinit var queue: FilePendingActionQueue

    private fun action(id: String, order: String = "o1", type: PendingActionType = PendingActionType.DELIVER, at: Long = 1L, note: String = "") =
        PendingAction(id, type, order, note, createdAtMillis = at)

    @Before
    fun setUp() {
        file = File(tmp.root, "pending/actions.json")
        queue = FilePendingActionQueue(file)
    }

    @Test
    fun `empty queue has nothing`() {
        assertTrue(queue.pending().isEmpty())
        assertTrue(queue.rejected().isEmpty())
    }

    @Test
    fun `actions survive a new instance and come back oldest first`() {
        queue.enqueue(action("b", "o2", at = 20L, note = "ausente"))
        queue.enqueue(action("a", "o1", at = 10L))

        val reopened = FilePendingActionQueue(file).pending()
        assertEquals(listOf("a", "b"), reopened.map { it.id })
        assertEquals("ausente", reopened[1].note)
        assertEquals(null, reopened[0].lastError)
    }

    @Test
    fun `same type for the same order is not queued twice, different type is`() {
        assertTrue(queue.enqueue(action("a", "o1")))
        assertFalse(queue.enqueue(action("b", "o1")))
        assertTrue(queue.enqueue(action("c", "o1", type = PendingActionType.FAIL)))
        assertEquals(listOf("a", "c"), queue.pending().map { it.id })
    }

    @Test
    fun `remove drops only that action`() {
        queue.enqueue(action("a", "o1"))
        queue.enqueue(action("b", "o2"))
        queue.remove("a")
        assertEquals(listOf("b"), queue.pending().map { it.id })
    }

    @Test
    fun `recordFailure counts attempts and keeps the last error`() {
        queue.enqueue(action("a"))
        queue.recordFailure("a", "timeout")
        queue.recordFailure("a", "sem rede")
        val a = queue.pending().single()
        assertEquals(2, a.attempts)
        assertEquals("sem rede", a.lastError)
    }

    @Test
    fun `rejected actions leave pending but stay stored until cleared`() {
        queue.enqueue(action("a", "o1"))
        queue.enqueue(action("b", "o2"))
        queue.markRejected("a", "ja entregue")

        assertEquals(listOf("b"), queue.pending().map { it.id })
        assertEquals("ja entregue", queue.rejected().single().rejectedReason)

        queue.clearRejected()
        assertTrue(queue.rejected().isEmpty())
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `an order rejected earlier can be queued again`() {
        queue.enqueue(action("a", "o1"))
        queue.markRejected("a", "x")
        assertTrue(queue.enqueue(action("b", "o1")))
    }

    @Test
    fun `corrupt file is set aside instead of crashing or being overwritten silently`() {
        file.parentFile.mkdirs()
        file.writeText("[[not json")
        assertTrue(queue.pending().isEmpty())
        assertTrue(File(file.parentFile, "actions.json.corrupt").exists())
        assertTrue(queue.enqueue(action("a")))
    }

    @Test
    fun `clear empties the queue`() {
        queue.enqueue(action("a"))
        queue.clear()
        assertTrue(queue.pending().isEmpty())
    }
}
