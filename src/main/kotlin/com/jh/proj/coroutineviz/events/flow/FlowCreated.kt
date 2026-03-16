package com.jh.proj.coroutineviz.events.flow

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when an instrumented Flow is created.
 *
 * This event marks the creation of a Flow that will emit values.
 * Different flow types have different behaviors regarding collection
 * and value sharing.
 *
 * @property coroutineId ID of the coroutine that created the flow
 * @property flowId Unique identifier for this Flow instance
 * @property flowType Type of flow: "Cold", "Hot", "StateFlow", "SharedFlow"
 * @property label Optional human-readable label
 * @property scopeId ID of the owning VizScope, if applicable
 */
@Serializable
@SerialName("FlowCreated")
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
    override val kind: String get() = "FlowCreated"
}
