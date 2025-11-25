package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
data class FlowValueEmitted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val collectorId: String,
    val sequenceNumber: Int,
    val valuePreview: String,  // toString() of value, truncated to 200 chars
    val valueType: String  // Class name of the value
) : VizEvent {
    override val kind = "FlowValueEmitted"
}