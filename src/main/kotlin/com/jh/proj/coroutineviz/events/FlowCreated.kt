package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
data class FlowCreated(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val flowType: String,  // "Cold", "Hot", "StateFlow", "SharedFlow"
    val label: String? = null,
    val scopeId: String? = null
) : VizEvent {
    override val kind = "FlowCreated"
}
