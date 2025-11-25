package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
data class FlowCollectionStarted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val collectorId: String,
    val label: String? = null
) : VizEvent {
    override val kind = "FlowCollectionStarted"
}