package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a coroutine begins executing its body.
 *
 * This event marks the transition from CREATED to ACTIVE state.
 * The coroutine is now running on a thread.
 *
 * Lifecycle: [CoroutineCreated] → STARTED → (execution)
 */
@Serializable
@SerialName("CoroutineStarted")
data class CoroutineStarted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?
) : CoroutineEvent {
    override val kind: String get() = "CoroutineStarted"
}
