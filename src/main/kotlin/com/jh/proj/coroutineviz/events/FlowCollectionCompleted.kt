package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
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
    override val kind = "FlowCollectionCompleted"
}