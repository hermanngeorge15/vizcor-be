package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

@Serializable
sealed interface CoroutineEvent: VizEvent {
    val coroutineId: String
    val jobId: String
    val parentCoroutineId: String?
    val scopeId: String
    val label: String?
}