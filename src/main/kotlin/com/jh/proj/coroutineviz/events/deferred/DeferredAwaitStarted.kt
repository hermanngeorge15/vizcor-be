package com.jh.proj.coroutineviz.events.deferred

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when [Deferred.await] is called.
 *
 * This event marks the start of an await operation. The awaiting coroutine
 * will suspend until the deferred value is available. Pairs with
 * [DeferredAwaitCompleted] when the await finishes.
 *
 * @property deferredId Unique identifier for the Deferred instance
 * @property coroutineId ID of the coroutine that owns the Deferred
 * @property awaiterId ID of the coroutine calling await(), if known
 * @property scopeId ID of the owning VizScope
 * @property label Optional label for the deferred
 */
@Serializable
@SerialName("DeferredAwaitStarted")
data class DeferredAwaitStarted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val deferredId: String,
    val coroutineId: String,      // The deferred's coroutine
    val awaiterId: String?,       // Who is waiting
    val scopeId: String,
    val label: String?
) : VizEvent {
    override val kind: String get() = "DeferredAwaitStarted"
}
