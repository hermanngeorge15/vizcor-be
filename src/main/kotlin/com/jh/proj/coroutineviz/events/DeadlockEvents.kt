package com.jh.proj.coroutineviz.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// Deadlock Detection Events
// ============================================================================

/**
 * Emitted when a deadlock is detected in the system
 */
@Serializable
@SerialName("DeadlockDetected")
data class DeadlockDetected(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val involvedCoroutines: List<String>,
    val involvedCoroutineLabels: List<String?>,
    val involvedMutexes: List<String>,
    val involvedMutexLabels: List<String?>,
    val waitGraph: Map<String, String>,    // coroutineId -> waitingForMutexId
    val holdGraph: Map<String, String>,    // mutexId -> heldByCoroutineId
    val cycleDescription: String           // Human-readable cycle description
) : VizEvent {
    override val kind: String get() = "DeadlockDetected"
}

/**
 * Emitted when a potential deadlock pattern is detected (warning, not actual deadlock)
 */
@Serializable
@SerialName("PotentialDeadlockWarning")
data class PotentialDeadlockWarning(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val coroutineId: String,
    val coroutineLabel: String?,
    val holdingMutex: String,
    val holdingMutexLabel: String?,
    val requestingMutex: String,
    val requestingMutexLabel: String?,
    val recommendation: String
) : VizEvent {
    override val kind: String get() = "PotentialDeadlockWarning"
}

/**
 * Represents a node in the wait-for graph for deadlock detection
 */
data class WaitGraphNode(
    val coroutineId: String,
    val coroutineLabel: String?,
    val holdingMutexIds: Set<String>,
    val waitingForMutexId: String?
)

/**
 * Result of deadlock analysis
 */
sealed class DeadlockAnalysisResult {
    data object NoDeadlock : DeadlockAnalysisResult()
    
    data class DeadlockFound(
        val cycle: List<String>,           // Coroutine IDs in the cycle
        val cycleLabels: List<String?>,    // Corresponding labels
        val involvedMutexes: List<String>, // Mutex IDs involved
        val mutexLabels: List<String?>     // Corresponding labels
    ) : DeadlockAnalysisResult()
    
    data class PotentialDeadlock(
        val coroutineId: String,
        val holdingMutex: String,
        val requestingMutex: String,
        val reason: String
    ) : DeadlockAnalysisResult()
}

