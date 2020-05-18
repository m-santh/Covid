/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.referencecode

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import uk.nhs.nhsx.sonar.android.app.appComponent
import javax.inject.Inject

class ReferenceCodeWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    @Inject
    lateinit var work: ReferenceCodeWork

    override suspend fun doWork(): Result {
        appComponent.inject(this)
        return work.doWork()
    }
}

class ReferenceCodeWork @Inject constructor(
    private val api: ReferenceCodeApi,
    private val provider: ReferenceCodeProvider
) {
    suspend fun doWork(): Result =
        try {
            if (provider.get() == null) {
                doFetch()
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }

    private suspend fun doFetch() =
        api
            .generate()
            .toCoroutine()
            .let { provider.set(it) }
}
