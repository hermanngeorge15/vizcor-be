package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a Flow operator is applied to create a derived Flow.
 * Tracks the operator chain for visualization.
 */
@Serializable
data class FlowOperatorApplied(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val flowId: String,
    val sourceFlowId: String,  // The upstream flow this operator transforms
    val operatorName: String,  // "map", "filter", "transform", "flatMapConcat", etc.
    val operatorIndex: Int,    // Position in operator chain (0 = first operator)
    val label: String? = null,
    val coroutineId: String? = null  // If created within a coroutine context
) : VizEvent {
    override val kind = "FlowOperatorApplied"
}

