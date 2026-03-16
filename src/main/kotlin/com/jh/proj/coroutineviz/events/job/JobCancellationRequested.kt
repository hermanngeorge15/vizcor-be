package com.jh.proj.coroutineviz.events.job

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when [Job.cancel] is explicitly called.
 *
 * This marks the *request* for cancellation, not completion. The actual
 * cancellation may take time as the coroutine reaches suspension points.
 * [CoroutineCancelled] is emitted when cancellation actually completes.
 *
 * @property requestedBy ID of the coroutine/entity that requested cancellation
 * @property cause Reason for cancellation, if provided
 */
@Serializable
@SerialName("JobCancellationRequested")
data class JobCancellationRequested(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val requestedBy: String? = null,
    val cause: String? = null
) : CoroutineEvent {
    override val kind: String get() = "JobCancellationRequested"
}
