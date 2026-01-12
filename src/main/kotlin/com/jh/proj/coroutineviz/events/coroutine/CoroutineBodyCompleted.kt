package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a coroutine's own code has finished executing.
 *
 * This is distinct from [CoroutineCompleted] - at this point the coroutine's
 * body has finished, but it may still be waiting for child coroutines to
 * complete (structured concurrency). The coroutine transitions to
 * WAITING_FOR_CHILDREN state.
 *
 * Lifecycle: (body execution) → BODY_COMPLETED → [CoroutineCompleted]
 */
@Serializable
@SerialName("CoroutineBodyCompleted")
data class CoroutineBodyCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?
) : CoroutineEvent {
    override val kind: String get() = "CoroutineBodyCompleted"
}
