package com.jh.proj.coroutineviz.events.dispatcher

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a coroutine is assigned to a specific thread.
 *
 * This event is emitted when the coroutine actually starts running on a thread,
 * which may be different from when the dispatcher was selected. It enables
 * thread activity visualization showing which coroutines ran on which threads.
 *
 * @property threadId System thread ID (from [Thread.threadId])
 * @property threadName Human-readable thread name
 * @property dispatcherName Name of the dispatcher that assigned this thread
 */
@Serializable
@SerialName("ThreadAssigned")
data class ThreadAssigned(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val threadId: Long,
    val threadName: String,
    val dispatcherName: String?
) : CoroutineEvent {
    override val kind: String get() = "ThreadAssigned"
}
