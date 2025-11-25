package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
sealed interface VizEvent {
    val sessionId: String
    val seq: Long
    val tsNanos: Long
    val kind: String
}
