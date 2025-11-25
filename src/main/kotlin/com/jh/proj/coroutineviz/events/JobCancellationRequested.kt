package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Event emitted when job.cancel() is explicitly called.
 * This is different from CoroutineCancelled which is emitted when cancellation completes.
 */
@Serializable
data class JobCancellationRequested(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val requestedBy: String? = null, // Optional: who requested cancellation
    val cause: String? = null
) : CoroutineEvent {
    override val kind = "JobCancellationRequested"
}


