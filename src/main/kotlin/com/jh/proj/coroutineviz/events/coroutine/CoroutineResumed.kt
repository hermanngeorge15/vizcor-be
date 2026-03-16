package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a suspended coroutine resumes execution.
 *
 * This event marks the transition back from SUSPENDED to ACTIVE state.
 * The coroutine has been assigned a thread and continues executing
 * from where it suspended.
 *
 * Lifecycle: [CoroutineSuspended] → RESUMED → (continues execution)
 */
@Serializable
@SerialName("CoroutineResumed")
data class CoroutineResumed(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?
) : CoroutineEvent {
    override val kind: String get() = "CoroutineResumed"
}
