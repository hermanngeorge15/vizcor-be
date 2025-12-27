package com.jh.proj.coroutineviz.events.deferred

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("DeferredAwaitCompleted")
data class DeferredAwaitCompleted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val deferredId: String,
    val coroutineId: String,
    val awaiterId: String?,
    val scopeId: String,
    val label: String?
) : VizEvent {
    override val kind: String get() = "DeferredAwaitCompleted"
}
