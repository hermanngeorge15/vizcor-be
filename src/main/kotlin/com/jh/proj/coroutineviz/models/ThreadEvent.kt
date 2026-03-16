package com.jh.proj.coroutineviz.models

import kotlinx.serialization.Serializable

/**
 * Records a coroutine's assignment to or release from a thread.
 *
 * ThreadEvent is used by [ProjectionService] to track thread activity over time,
 * enabling visualizations of which coroutines ran on which threads and when.
 *
 * @property coroutineId ID of the coroutine involved
 * @property threadId System thread ID
 * @property threadName Human-readable thread name
 * @property timestamp Nanosecond timestamp of the event
 * @property eventType Type of thread event: "ASSIGNED" or "RELEASED"
 * @property dispatcherName Name of the dispatcher that assigned the thread
 */
@Serializable
data class ThreadEvent(
    val coroutineId: String,
    val threadId: Long,
    val threadName: String,
    val timestamp: Long,
    val eventType: String,  // "ASSIGNED", "RELEASED"
    val dispatcherName: String? = null
)
