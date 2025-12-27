package com.jh.proj.coroutineviz.events.dispatcher

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
