package com.jh.proj.coroutineviz.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// Mutex Event Types - For visualizing mutual exclusion synchronization
// ============================================================================

/**
 * Base interface for all Mutex-related events
 */
@Serializable
sealed interface MutexEvent : VizEvent {
    val mutexId: String
    val mutexLabel: String?
}

/**
 * Emitted when a new Mutex is created
 */
@Serializable
@SerialName("MutexCreated")
data class MutexCreated(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val mutexId: String,
    override val mutexLabel: String?,
    val ownerCoroutineId: String? = null
) : MutexEvent {
    override val kind: String get() = "MutexCreated"
}

/**
 * Emitted when a coroutine requests to acquire a mutex lock
 */
@Serializable
@SerialName("MutexLockRequested")
data class MutexLockRequested(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val mutexId: String,
    override val mutexLabel: String?,
    val requesterId: String,
    val requesterLabel: String?,
    val isLocked: Boolean,           // Current state when requested
    val queuePosition: Int           // Position in wait queue (0 if acquired immediately)
) : MutexEvent {
    override val kind: String get() = "MutexLockRequested"
}

/**
 * Emitted when a coroutine successfully acquires a mutex lock
 */
@Serializable
@SerialName("MutexLockAcquired")
data class MutexLockAcquired(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val mutexId: String,
    override val mutexLabel: String?,
    val acquirerId: String,
    val acquirerLabel: String?,
    val waitDurationNanos: Long      // How long waited for lock
) : MutexEvent {
    override val kind: String get() = "MutexLockAcquired"
}

/**
 * Emitted when a coroutine releases a mutex lock
 */
@Serializable
@SerialName("MutexUnlocked")
data class MutexUnlocked(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val mutexId: String,
    override val mutexLabel: String?,
    val releaserId: String,
    val releaserLabel: String?,
    val nextWaiterId: String?,       // Who gets the lock next
    val holdDurationNanos: Long      // How long lock was held
) : MutexEvent {
    override val kind: String get() = "MutexUnlocked"
}

/**
 * Emitted when a coroutine attempts tryLock and fails
 */
@Serializable
@SerialName("MutexTryLockFailed")
data class MutexTryLockFailed(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val mutexId: String,
    override val mutexLabel: String?,
    val requesterId: String,
    val requesterLabel: String?,
    val currentOwnerId: String?      // Who currently holds the lock
) : MutexEvent {
    override val kind: String get() = "MutexTryLockFailed"
}

/**
 * Emitted when the mutex wait queue changes
 */
@Serializable
@SerialName("MutexQueueChanged")
data class MutexQueueChanged(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    override val mutexId: String,
    override val mutexLabel: String?,
    val waitingCoroutineIds: List<String>,
    val waitingLabels: List<String?>
) : MutexEvent {
    override val kind: String get() = "MutexQueueChanged"
}

