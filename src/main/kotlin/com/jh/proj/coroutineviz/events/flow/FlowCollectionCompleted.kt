package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a Flow collection completes successfully.
 *
 * This indicates the flow finished emitting all values and the collector
 * has processed them. This is one of the terminal events for flow collection
 * (along with [FlowCollectionCancelled]).
 *
 * @property coroutineId ID of the collecting coroutine
 * @property flowId ID of the completed Flow
 * @property collectorId ID of the collection instance
 * @property totalEmissions Total number of values emitted
 * @property durationNanos Time from collection start to completion in nanoseconds
 */
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
