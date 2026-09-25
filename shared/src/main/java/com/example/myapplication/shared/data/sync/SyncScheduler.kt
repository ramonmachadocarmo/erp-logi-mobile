package com.example.myapplication.shared.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.shared.AppContainer
import java.util.concurrent.TimeUnit

/** Asks the OS to run [PendingActionSyncer] as soon as there is connectivity. */
fun interface SyncScheduler {
    fun schedule()
}

class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncPendingActionsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: an action queued while a pass is running still gets its own pass
        // (KEEP would drop it), and a finished/failed predecessor doesn't block the new one.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val UNIQUE_NAME = "pending-actions-sync"
    }
}

/**
 * Runs in the background even if the app UI is closed. Builds its own [AppContainer]; the session
 * token is read back from the encrypted TokenStore, the queue from its file.
 */
class SyncPendingActionsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = AppContainer(applicationContext).pendingActionSyncer.sync()
        // needsLogin: retrying would just 401 again; the app reschedules on next start/login.
        return if (outcome.retryLater) Result.retry() else Result.success()
    }
}
