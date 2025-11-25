package com.jh.proj.coroutineviz.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("DispatcherSelected")
data class DispatcherSelected(
    // Standard CoroutineEvent fields
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,

    // Dispatcher-specific fields
    val dispatcherId: String,        // Unique ID for dispatcher instance
    val dispatcherName: String,      // "Dispatchers.Default", "Dispatchers.IO", etc.
    val queueDepth: Int? = null      // Optional: queue size at dispatch time
) : CoroutineEvent{
    override val kind: String
        get() = "DispatcherSelected"
}