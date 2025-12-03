package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when backpressure is detected in a Flow.
 * This occurs when producer is faster than consumer.
 */
@Serializable
data class FlowBackpressure(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val flowId: String,
    val collectorId: String,
    val reason: String,  // "slow_collector", "buffer_full", "conflated"
    val pendingEmissions: Int,
    val bufferCapacity: Int?,
    val durationNanos: Long?,  // How long producer waited
    val coroutineId: String? = null
) : VizEvent {
    override val kind = "FlowBackpressure"
}

