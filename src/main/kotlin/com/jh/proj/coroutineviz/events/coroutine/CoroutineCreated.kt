package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a new coroutine is created via [vizLaunch] or [vizAsync].
 *
 * This is the first event in a coroutine's lifecycle. At this point the
 * coroutine exists but has not yet started executing.
 *
 * Lifecycle: CREATED → [CoroutineStarted]
 */
@Serializable
@SerialName("CoroutineCreated")
data class CoroutineCreated(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?
) : CoroutineEvent {
    override val kind: String get() = "CoroutineCreated"
}
