package com.jh.proj.coroutineviz.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val sessionId: String,
    val coroutineCount: Int,
    val eventCount: Int
)
