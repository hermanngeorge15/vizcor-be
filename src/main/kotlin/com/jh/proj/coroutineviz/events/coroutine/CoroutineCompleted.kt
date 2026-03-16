package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a coroutine has fully completed.
 *
 * This is a terminal event indicating successful completion. Both the
 * coroutine's own code and all child coroutines have finished successfully.
 * This is one of the three possible terminal states (along with
 * [CoroutineCancelled] and [CoroutineFailed]).
 *
 * Lifecycle: [CoroutineBodyCompleted] → COMPLETED (terminal)
 */
@Serializable
@SerialName("CoroutineCompleted")
data class CoroutineCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?
) : CoroutineEvent {
    override val kind: String get() = "CoroutineCompleted"
}
