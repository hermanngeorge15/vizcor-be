package com.jh.proj.coroutineviz.models

import kotlinx.serialization.Serializable

@Serializable
data class ThreadEvent(
    val coroutineId: String,
    val threadId: Long,
    val threadName: String,
    val timestamp: Long,
    val eventType: String,  // "ASSIGNED", "RELEASED"
    val dispatcherName: String? = null
)
