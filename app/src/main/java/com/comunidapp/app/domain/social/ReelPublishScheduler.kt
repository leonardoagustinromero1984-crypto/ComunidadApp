package com.comunidapp.app.domain.social

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

object ReelPublishScheduler {
    const val UNIQUE_WORK = "leover-reel-publish"
    const val KEY_JOB_ID = "job_id"

    fun enqueue(context: Context, jobId: String, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<ReelPublishWorker>()
            .setInputData(workDataOf(KEY_JOB_ID to jobId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }

    suspend fun restoreAndResume(context: Context) {
        val controller = ReelPublishController.initialize(context)
        controller.restore()
        val job = controller.job.value ?: return
        val currentUserId = com.comunidapp.app.data.repository.AuthProvider.repository.getCurrentUser()?.id
        if (!job.belongsTo(currentUserId) || !job.shouldKeepForResume) return
        enqueue(context, job.jobId, replace = false)
    }
}

class ReelPublishWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(ReelPublishScheduler.KEY_JOB_ID) ?: return Result.failure()
        ReelPublishController.initialize(applicationContext)
        ReelPublishController.get().runJob(applicationContext, jobId)
        return Result.success()
    }
}
