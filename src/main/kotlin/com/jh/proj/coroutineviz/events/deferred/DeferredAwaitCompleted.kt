package com.jh.proj.coroutineviz.events.deferred

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when [Deferred.await] completes and returns the value.
 *
 * This event follows [DeferredAwaitStarted] and indicates the awaiting
 * coroutine has received the deferred value and will resume execution.
 *
 * @property deferredId Unique identifier for the Deferred instance
 * @property coroutineId ID of the coroutine that owns the Deferred
 * @property awaiterId ID of the coroutine that called await(), if known
 * @property scopeId ID of the owning VizScope
 * @property label Optional label for the deferred
 */
@Serializable
@SerialName("DeferredAwaitCompleted")
data class DeferredAwaitCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val deferredId: String,
    val coroutineId: String,
    val awaiterId: String?,
    val scopeId: String,
    val label: String?
) : VizEvent {
    override val kind: String get() = "DeferredAwaitCompleted"
}
