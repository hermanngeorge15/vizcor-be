package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a StateFlow's value changes.
 */
@Serializable
data class StateFlowValueChanged(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val flowId: String,
    val oldValuePreview: String,
    val newValuePreview: String,
    val valueType: String,
    val subscriberCount: Int,  // Number of active collectors
    val coroutineId: String? = null,
    val label: String? = null
) : VizEvent {
    override val kind = "StateFlowValueChanged"
}

