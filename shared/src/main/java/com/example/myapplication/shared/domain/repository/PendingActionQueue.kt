package com.example.myapplication.shared.domain.repository

import com.example.myapplication.shared.domain.model.PendingAction

/** Durable FIFO of writes made offline; survives process death and reboots. */
interface PendingActionQueue {
    /** Adds [action]; returns false (and adds nothing) if the same type is already queued for that order. */
    fun enqueue(action: PendingAction): Boolean

    /** Actions still to be sent, oldest first (excludes rejected ones). */
    fun pending(): List<PendingAction>

    /** Actions the server permanently refused. */
    fun rejected(): List<PendingAction>

    fun remove(id: String)

    /** Counts a failed send attempt (transient failure) on [id]. */
    fun recordFailure(id: String, error: String)

    /** Marks [id] as permanently refused by the server; it is kept but never retried. */
    fun markRejected(id: String, reason: String)

    /** Forgets the rejected actions once they've been shown to the driver. */
    fun clearRejected()

    fun clear()
}
