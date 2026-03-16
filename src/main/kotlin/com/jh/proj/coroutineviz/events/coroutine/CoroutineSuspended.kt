package com.jh.proj.coroutineviz.events.coroutine

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.SuspensionPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Emitted when a coroutine suspends execution.
 *
 * Suspension occurs at suspension points like [delay], [await], [join],
 * or [withContext]. The coroutine releases its thread and will be
 * resumed later when the suspending operation completes.
 *
 * @property reason Why the coroutine suspended (e.g., "delay", "await", "join")
 * @property durationMillis Expected duration if known (e.g., for delay)
 * @property suspensionPoint Source code location where suspension occurred
 *
 * Lifecycle: ACTIVE → SUSPENDED → [CoroutineResumed]
 */
@Serializable
@SerialName("CoroutineSuspended")
data class CoroutineSuspended(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val reason: String,
    val durationMillis: Long? = null,
    val suspensionPoint: SuspensionPoint? = null
) : CoroutineEvent {
    override val kind: String get() = "CoroutineSuspended"
}
