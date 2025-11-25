package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
data class FlowCollectionCancelled(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val collectorId: String,
    val reason: String?,
    val emittedCount: Int
) : VizEvent {
    override val kind = "FlowCollectionCancelled"
}
