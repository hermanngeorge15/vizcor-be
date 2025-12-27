package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("CoroutineCancelled")
data class CoroutineCancelled(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val cause: String?
) : CoroutineEvent {
    override val kind: String get() = "CoroutineCancelled"
}
