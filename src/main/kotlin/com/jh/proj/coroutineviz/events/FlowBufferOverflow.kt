package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
data class FlowBufferOverflow(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val droppedValue: String?,
    val bufferSize: Int,
    val overflowStrategy: String  // "SUSPEND", "DROP_LATEST", "DROP_OLDEST"
) : VizEvent {
    override val kind = "FlowBufferOverflow"
}