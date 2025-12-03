package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a value is emitted to a SharedFlow.
 */
@Serializable
data class SharedFlowEmission(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val flowId: String,
    val valuePreview: String,
    val valueType: String,
    val subscriberCount: Int,
    val replayCache: Int,  // Current replay cache size
    val extraBufferCapacity: Int,
    val coroutineId: String? = null,
    val label: String? = null
) : VizEvent {
    override val kind = "SharedFlowEmission"
}

