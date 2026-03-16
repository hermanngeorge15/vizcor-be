package com.jh.proj.coroutineviz.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// Semaphore Event Types - For visualizing permit-based synchronization
// ============================================================================

/**
 * Base interface for all Semaphore-related events
 */
@Serializable
sealed interface SemaphoreEvent : VizEvent {
    val semaphoreId: String
    val semaphoreLabel: String?
}

/**
 * Emitted when a new Semaphore is created
 */
@Serializable
@SerialName("SemaphoreCreated")
data class SemaphoreCreated(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val semaphoreId: String,
    override val semaphoreLabel: String?,
    val totalPermits: Int
) : SemaphoreEvent {
    override val kind: String get() = "SemaphoreCreated"
}

/**
 * Emitted when a coroutine requests to acquire a permit
 */
@Serializable
@SerialName("SemaphoreAcquireRequested")
data class SemaphoreAcquireRequested(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val semaphoreId: String,
    override val semaphoreLabel: String?,
    val requesterId: String,
    val requesterLabel: String?,
    val availablePermits: Int,
    val permitsRequested: Int        // Usually 1, but can be more
) : SemaphoreEvent {
    override val kind: String get() = "SemaphoreAcquireRequested"
}

/**
 * Emitted when a coroutine successfully acquires a permit
 */
@Serializable
@SerialName("SemaphorePermitAcquired")
data class SemaphorePermitAcquired(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val semaphoreId: String,
    override val semaphoreLabel: String?,
    val acquirerId: String,
    val acquirerLabel: String?,
    val remainingPermits: Int,
    val waitDurationNanos: Long
) : SemaphoreEvent {
    override val kind: String get() = "SemaphorePermitAcquired"
}

/**
 * Emitted when a coroutine releases a permit
 */
@Serializable
@SerialName("SemaphorePermitReleased")
data class SemaphorePermitReleased(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val semaphoreId: String,
    override val semaphoreLabel: String?,
    val releaserId: String,
    val releaserLabel: String?,
    val newAvailablePermits: Int,
    val holdDurationNanos: Long
) : SemaphoreEvent {
    override val kind: String get() = "SemaphorePermitReleased"
}

/**
 * Emitted when a coroutine attempts tryAcquire and fails
 */
@Serializable
@SerialName("SemaphoreTryAcquireFailed")
data class SemaphoreTryAcquireFailed(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val semaphoreId: String,
    override val semaphoreLabel: String?,
    val requesterId: String,
    val requesterLabel: String?,
    val availablePermits: Int,
    val permitsRequested: Int
) : SemaphoreEvent {
    override val kind: String get() = "SemaphoreTryAcquireFailed"
}

/**
 * Emitted when the overall semaphore state changes
 */
@Serializable
@SerialName("SemaphoreStateChanged")
data class SemaphoreStateChanged(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val semaphoreId: String,
    override val semaphoreLabel: String?,
    val availablePermits: Int,
    val totalPermits: Int,
    val activeHolders: List<String>,
    val activeHolderLabels: List<String?>,
    val waitingCoroutines: List<String>,
    val waitingLabels: List<String?>
) : SemaphoreEvent {
    override val kind: String get() = "SemaphoreStateChanged"
}

