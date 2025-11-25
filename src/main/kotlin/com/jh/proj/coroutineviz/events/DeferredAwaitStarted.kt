package com.jh.proj.coroutineviz.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("DeferredAwaitStarted")
data class DeferredAwaitStarted(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val deferredId: String,
    val coroutineId: String,      // The deferred's coroutine
    val awaiterId: String?,       // Who is waiting
    val scopeId: String,
    val label: String?
) : VizEvent {  // Note: Not CoroutineEvent, different fields
    override val kind: String
        get() = "DeferredAwaitStarted"
}