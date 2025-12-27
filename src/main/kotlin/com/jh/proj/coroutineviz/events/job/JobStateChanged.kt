package com.jh.proj.coroutineviz.events.job

import com.jh.proj.coroutineviz.events.CoroutineEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Event emitted when a Job's state changes.
 * Tracks the real-time state of jobs for frontend visualization.
 */
@Serializable
@SerialName("JobStateChanged")
data class JobStateChanged(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val coroutineId: String,
    override val jobId: String,
    override val parentCoroutineId: String?,
    override val scopeId: String,
    override val label: String?,
    val isActive: Boolean,
    val isCompleted: Boolean,
    val isCancelled: Boolean,
    val childrenCount: Int = 0
) : CoroutineEvent {
    override val kind: String get() = "JobStateChanged"
}
