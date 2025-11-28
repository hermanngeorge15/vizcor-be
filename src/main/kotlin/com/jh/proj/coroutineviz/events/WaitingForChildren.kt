package com.jh.proj.coroutineviz.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("WaitingForChildren")
data class WaitingForChildren(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val activeChildrenCount: Int,
    val activeChildrenIds: List<String>  // Coroutine IDs of active children
) : CoroutineEvent {
    override val kind: String get() = "WaitingForChildren"
}