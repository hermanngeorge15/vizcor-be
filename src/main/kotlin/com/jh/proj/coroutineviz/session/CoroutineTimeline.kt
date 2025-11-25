package com.jh.proj.coroutineviz.session

import kotlinx.serialization.Serializable

/**
 * Timeline view for a specific coroutine, with computed durations and aggregated information.
 */
@Serializable
data class CoroutineTimeline(
    val coroutineId: String,
    val name: String,
    val state: String,
    val totalDuration: Long?,  // Total time from creation to completion (nanos)
    val activeDuration: Long? = null,  // Time spent actively running (not suspended)
    val suspendedDuration: Long? = null,  // Time spent suspended
    val parentId: String? = null,
    val childrenIds: List<String> = emptyList(),
    val events: List<TimelineEventSummary> = emptyList()
)

/**
 * Simplified event summary for timeline display
 */
@Serializable
data class TimelineEventSummary(
    val seq: Long,
    val tsNanos: Long,
    val kind: String,
    val threadName: String? = null,
    val dispatcherName: String? = null,
    val reason: String? = null  // For suspension events
)

