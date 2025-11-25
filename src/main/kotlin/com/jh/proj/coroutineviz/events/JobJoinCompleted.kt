package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Event emitted when job.join() completes (the job has finished).
 */
@Serializable
data class JobJoinCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val waitingCoroutineId: String? = null
) : CoroutineEvent {
    override val kind = "JobJoinCompleted"
}


