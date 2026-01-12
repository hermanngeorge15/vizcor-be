package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a coroutine fails with an unhandled exception.
 *
 * This is a terminal event indicating the coroutine threw an exception
 * that was not caught. In structured concurrency, this typically causes
 * the parent and siblings to be cancelled as well.
 *
 * @property exceptionType Fully qualified exception class name
 * @property message Exception message
 * @property stackTrace Stack trace frames for debugging
 *
 * Lifecycle: (any state) → FAILED (terminal)
 */
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
