package com.example.myapplication.shared.data.cache

import com.example.myapplication.shared.domain.model.PendingAction
import com.example.myapplication.shared.domain.model.PendingActionType
import com.example.myapplication.shared.domain.repository.PendingActionQueue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Whole queue in one JSON file, rewritten atomically (temp + rename) on every change. The lock is
 * process-wide (companion) because the UI and the background sync worker each build their own
 * AppContainer, hence their own instance over the same file.
 */
class FilePendingActionQueue(private val file: File) : PendingActionQueue {

    override fun enqueue(action: PendingAction): Boolean = synchronized(LOCK) {
        val all = load()
        if (all.any { it.type == action.type && it.salesOrderId == action.salesOrderId && !it.isRejected }) {
            return false
        }
        store(all + action)
        true
    }

    override fun pending(): List<PendingAction> = synchronized(LOCK) {
        load().filterNot { it.isRejected }.sortedBy { it.createdAtMillis }
    }

    override fun rejected(): List<PendingAction> = synchronized(LOCK) {
        load().filter { it.isRejected }.sortedBy { it.createdAtMillis }
    }

    override fun remove(id: String) = synchronized(LOCK) { store(load().filterNot { it.id == id }) }

    override fun recordFailure(id: String, error: String) = synchronized(LOCK) {
        store(load().map { if (it.id == id) it.copy(attempts = it.attempts + 1, lastError = error) else it })
    }

    override fun markRejected(id: String, reason: String) = synchronized(LOCK) {
        store(load().map { if (it.id == id) it.copy(attempts = it.attempts + 1, rejectedReason = reason) else it })
    }

    override fun clearRejected() = synchronized(LOCK) { store(load().filterNot { it.isRejected }) }

    override fun clear() = synchronized(LOCK) {
        file.delete()
        Unit
    }

    private fun load(): List<PendingAction> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PendingAction(
                    id = o.getString("id"),
                    type = PendingActionType.valueOf(o.getString("type")),
                    salesOrderId = o.getString("salesOrderId"),
                    note = o.optString("note"),
                    createdAtMillis = o.getLong("createdAt"),
                    attempts = o.optInt("attempts"),
                    lastError = if (o.isNull("lastError")) null else o.getString("lastError"),
                    rejectedReason = if (o.isNull("rejectedReason")) null else o.getString("rejectedReason"),
                )
            }
        }.getOrElse {
            // Unreadable file: keep it aside for inspection rather than silently dropping deliveries.
            file.renameTo(File(file.parentFile, file.name + ".corrupt"))
            emptyList()
        }
    }

    private fun store(actions: List<PendingAction>) {
        file.parentFile?.mkdirs()
        val arr = JSONArray()
        actions.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("type", a.type.name)
                    .put("salesOrderId", a.salesOrderId)
                    .put("note", a.note)
                    .put("createdAt", a.createdAtMillis)
                    .put("attempts", a.attempts)
                    .put("lastError", a.lastError ?: JSONObject.NULL)
                    .put("rejectedReason", a.rejectedReason ?: JSONObject.NULL),
            )
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
