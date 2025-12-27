package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("CoroutineFailed")
data class CoroutineFailed(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val exceptionType: String?,
    val message: String?,
    val stackTrace: List<String>
) : CoroutineEvent {
    override val kind: String get() = "CoroutineFailed"
}
