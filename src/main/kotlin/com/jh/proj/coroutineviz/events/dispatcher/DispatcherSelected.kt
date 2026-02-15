package com.jh.proj.coroutineviz.events.dispatcher

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a dispatcher is selected for a coroutine.
 *
 * This event is emitted by [InstrumentedDispatcher] when dispatch() is called,
 * before the coroutine is assigned to a thread. It indicates which dispatcher
 * will handle the coroutine's execution.
 *
 * @property dispatcherId Unique identifier for the dispatcher instance
 * @property dispatcherName Human-readable name (e.g., "Dispatchers.Default")
 * @property queueDepth Number of pending tasks in dispatcher queue, if available
 */
@Serializable
@SerialName("DispatcherSelected")
data class DispatcherSelected(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val dispatcherId: String,
    val dispatcherName: String,
    val queueDepth: Int? = null
) : CoroutineEvent {
    override val kind: String get() = "DispatcherSelected"
}
