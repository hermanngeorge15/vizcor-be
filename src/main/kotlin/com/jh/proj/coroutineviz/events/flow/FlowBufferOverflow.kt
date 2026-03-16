package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a buffered Flow's buffer overflows.
 *
 * This event indicates backpressure - the producer is emitting values faster
 * than the collector can process them. Depending on the overflow strategy,
 * values may be dropped or the producer may suspend.
 *
 * @property coroutineId ID of the coroutine involved
 * @property flowId ID of the Flow with the overflowing buffer
 * @property droppedValue Preview of the dropped value, if applicable
 * @property bufferSize Current size of the buffer
 * @property overflowStrategy Strategy used: "SUSPEND", "DROP_LATEST", or "DROP_OLDEST"
 */
@Serializable
@SerialName("FlowBufferOverflow")
data class FlowBufferOverflow(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val flowId: String,
    val droppedValue: String?,
    val bufferSize: Int,
    val overflowStrategy: String  // "SUSPEND", "DROP_LATEST", "DROP_OLDEST"
) : VizEvent {
    override val kind: String get() = "FlowBufferOverflow"
}
