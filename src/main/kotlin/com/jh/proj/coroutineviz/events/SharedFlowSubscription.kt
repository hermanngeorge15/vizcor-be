package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a collector subscribes to or unsubscribes from a SharedFlow.
 */
@Serializable
data class SharedFlowSubscription(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val flowId: String,
    val collectorId: String,
    val action: String,  // "subscribed" or "unsubscribed"
    val subscriberCount: Int,  // Total subscribers after this action
    val coroutineId: String? = null,
    val label: String? = null
) : VizEvent {
    override val kind = "SharedFlowSubscription"
}

