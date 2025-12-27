package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("FlowCollectionCompleted")
data class FlowCollectionCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val collectorId: String,
    val totalEmissions: Int,
    val durationNanos: Long
) : VizEvent {
    override val kind: String get() = "FlowCollectionCompleted"
}
