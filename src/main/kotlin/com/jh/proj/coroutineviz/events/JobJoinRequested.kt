package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Event emitted when job.join() is called, indicating a coroutine is waiting for this job.
 */
@Serializable
data class JobJoinRequested(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val waitingCoroutineId: String? = null // Which coroutine is waiting
) : CoroutineEvent {
    override val kind = "JobJoinRequested"
}


