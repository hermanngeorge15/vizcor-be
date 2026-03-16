package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a coroutine starts collecting from a Flow.
 *
 * Collection begins when [collect], [first], [toList], or similar terminal
 * operators are called. For cold flows, this triggers the flow to start
 * producing values.
 *
 * @property coroutineId ID of the coroutine collecting the flow
 * @property flowId ID of the Flow being collected
 * @property collectorId Unique identifier for this collection instance
 * @property label Optional human-readable label
 */
@Serializable
@SerialName("FlowCollectionStarted")
data class FlowCollectionStarted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val collectorId: String,
    val label: String? = null
) : VizEvent {
    override val kind: String get() = "FlowCollectionStarted"
}
