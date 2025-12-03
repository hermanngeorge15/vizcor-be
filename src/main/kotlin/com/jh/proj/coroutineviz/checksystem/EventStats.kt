package com.jh.proj.coroutineviz.checksystem

/**
 * Statistics about recorded events.
 */
data class EventStats(
    val totalEvents: Int,
    val byKind: Map<String, Int>,
    val coroutineCount: Int,
    val labelCount: Int
)