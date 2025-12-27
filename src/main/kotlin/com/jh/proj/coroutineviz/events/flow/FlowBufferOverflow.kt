package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("FlowBufferOverflow")
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
    override val kind: String get() = "FlowBufferOverflow"
}
