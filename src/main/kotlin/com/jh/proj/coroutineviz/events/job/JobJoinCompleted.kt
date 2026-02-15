package com.jh.proj.coroutineviz.events.job

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when [Job.join] completes and the waiting coroutine resumes.
 *
 * This event follows [JobJoinRequested] and indicates the job has finished
 * (either completed, cancelled, or failed). The waiting coroutine will
 * now resume execution.
 *
 * @property waitingCoroutineId ID of the coroutine that called join(), if known
 */
@Serializable
@SerialName("JobJoinCompleted")
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
    override val kind: String get() = "JobJoinCompleted"
}
