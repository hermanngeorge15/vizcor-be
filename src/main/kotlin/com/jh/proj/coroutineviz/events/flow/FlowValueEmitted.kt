package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("FlowValueEmitted")
data class FlowValueEmitted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val collectorId: String,
    val sequenceNumber: Int,
    val valuePreview: String,  // toString() of value, truncated to 200 chars
    val valueType: String      // Class name of the value
) : VizEvent {
    override val kind: String get() = "FlowValueEmitted"
}
