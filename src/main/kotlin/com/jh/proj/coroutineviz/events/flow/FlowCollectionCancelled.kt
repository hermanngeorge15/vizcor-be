package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a Flow collection is cancelled before completion.
 *
 * Collection can be cancelled due to coroutine cancellation, explicit
 * cancellation, or operators like [take] that cancel after receiving
 * enough values.
 *
 * @property coroutineId ID of the collecting coroutine
 * @property flowId ID of the cancelled Flow
 * @property collectorId ID of the collection instance
 * @property reason Description of why collection was cancelled
 * @property emittedCount Number of values emitted before cancellation
 */
@Serializable
@SerialName("FlowCollectionCancelled")
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
    override val kind: String get() = "FlowCollectionCancelled"
}
