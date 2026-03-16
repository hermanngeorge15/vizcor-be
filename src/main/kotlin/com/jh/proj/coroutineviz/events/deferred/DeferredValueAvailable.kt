package com.jh.proj.coroutineviz.events.deferred

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a Deferred's value becomes available.
 *
 * This event is emitted when the async coroutine completes and its result
 * is ready to be retrieved via [await]. Any coroutines waiting on this
 * deferred will be able to resume.
 *
 * @property deferredId Unique identifier for the Deferred instance
 */
@Serializable
@SerialName("DeferredValueAvailable")
data class DeferredValueAvailable(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val deferredId: String
) : CoroutineEvent {
    override val kind: String get() = "DeferredValueAvailable"
}
