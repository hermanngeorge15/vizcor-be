package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a value is filtered (either passed or dropped) by a filter operator.
 */
@Serializable
data class FlowValueFiltered(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val flowId: String,
    val operatorName: String,  // "filter", "filterNot", "filterIsInstance", "distinctUntilChanged"
    val valuePreview: String,
    val valueType: String,
    val passed: Boolean,  // true = value passed filter, false = value dropped
    val sequenceNumber: Int,
    val coroutineId: String? = null,
    val collectorId: String? = null
) : VizEvent {
    override val kind = "FlowValueFiltered"
}

