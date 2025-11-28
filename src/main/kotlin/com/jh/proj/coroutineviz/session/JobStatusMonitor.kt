package com.jh.proj.coroutineviz.session

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class JobStatusMonitor(
    private val session: VizSession,
    private val poolingIntervalMs: Long = 30L,
) {

    private val trackedJobs = ConcurrentHashMap<String, TrackedJob>()
    private var monitorJob: Job? = null

    fun track(job: Job, jobId: String, coroutineId: String) {
        trackedJobs[coroutineId] = TrackedJob(
            coroutineId = coroutineId,
            jobId = jobId,
            job = job,
            lastChildrenCount = job.children.count()
        )
    }

    fun start(){
        monitorJob = session.sessionScope.launch {
            while(isActive){
                delay(poolingIntervalMs)
                pollAllJobs()
            }
        }
    }

    private fun pollAllJobs() {
        trackedJobs.values.forEach { tracked ->
            val currentChildrenCount = tracked.job.children.count()
            // Only emit if children count changed
            if (currentChildrenCount != tracked.lastChildrenCount) {
                val ctx = EventContext(
                    session = session,
                    coroutineId = tracked.coroutineId,
                    jobId = tracked.jobId,
                    parentCoroutineId = null,  // Could enhance this
                    scopeId = "unknown",
                    label = null
                )

                session.send(ctx.jobStateChanged(
                    isActive = tracked.job.isActive,
                    isCompleted = tracked.job.isCompleted,
                    isCancelled = tracked.job.isCancelled,
                    childrenCount = currentChildrenCount
                ))

                tracked.lastChildrenCount = currentChildrenCount
            }

            // Cleanup completed jobs
            if (tracked.job.isCompleted) {
                trackedJobs.remove(tracked.coroutineId)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
    }


    private data class TrackedJob(
        val coroutineId: String,
        val jobId: String,
        val job: Job,
        var lastChildrenCount: Int,
    )
}