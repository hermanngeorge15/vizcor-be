package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a value passes through a transform operator.
 * Tracks input -> output transformation for visualization.
 */
@Serializable
data class FlowValueTransformed(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val flowId: String,
    val operatorName: String,
    val inputValuePreview: String,
    val outputValuePreview: String,
    val inputType: String,
    val outputType: String,
    val sequenceNumber: Int,
    val coroutineId: String? = null,
    val collectorId: String? = null
) : VizEvent {
    override val kind = "FlowValueTransformed"
}

