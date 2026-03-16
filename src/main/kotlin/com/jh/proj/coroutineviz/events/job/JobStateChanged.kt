package com.jh.proj.coroutineviz.events.job

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a Job's state changes.
 *
 * Tracks the real-time state of jobs for frontend visualization.
 * Job state is independent of coroutine state - a job can be cancelled
 * while the coroutine is still completing its cancellation logic.
 *
 * @property isActive True if the job is still running
 * @property isCompleted True if the job has finished (successfully or not)
 * @property isCancelled True if the job was cancelled
 * @property childrenCount Number of active child jobs
 */
@Serializable
@SerialName("JobStateChanged")
data class JobStateChanged(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val isActive: Boolean,
    val isCompleted: Boolean,
    val isCancelled: Boolean,
    val childrenCount: Int = 0
) : CoroutineEvent {
    override val kind: String get() = "JobStateChanged"
}
