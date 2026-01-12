package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a Flow emits a value to a collector.
 *
 * This event is emitted for each value that flows from producer to collector.
 * The value is captured as a preview string for debugging purposes.
 *
 * @property coroutineId ID of the coroutine involved in the emission
 * @property flowId ID of the Flow emitting the value
 * @property collectorId ID of the collector receiving the value
 * @property sequenceNumber Order of this emission (0-indexed)
 * @property valuePreview String representation of the value (truncated to 200 chars)
 * @property valueType Fully qualified class name of the emitted value
 */
@Serializable
@SerialName("FlowValueEmitted")
data class FlowValueEmitted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val collectorId: String,
    val sequenceNumber: Int,
    val valuePreview: String,  // toString() of value, truncated to 200 chars
    val valueType: String      // Class name of the value
) : VizEvent {
    override val kind: String get() = "FlowValueEmitted"
}
