package com.jh.proj.coroutineviz.events.job

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when [Job.join] is called on a job.
 *
 * This indicates a coroutine is suspending to wait for this job to complete.
 * The waiting coroutine will remain suspended until the job finishes,
 * at which point [JobJoinCompleted] is emitted.
 *
 * @property waitingCoroutineId ID of the coroutine that called join(), if known
 */
@Serializable
@SerialName("JobJoinRequested")
data class JobJoinRequested(
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
    override val kind: String get() = "JobJoinRequested"
}
