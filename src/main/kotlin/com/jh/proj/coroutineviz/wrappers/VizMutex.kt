package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Instrumented Mutex wrapper that emits visualization events.
 * 
 * This wrapper provides full transparency into mutex behavior:
 * - Lock acquisition and release timing
 * - Wait queue management
 * - Contention detection
 * - Potential deadlock warnings
 * 
 * Real-world use cases:
 * - Database connection serialization
 * - Shared resource protection
 * - Thread-safe counter updates
 * - Critical section protection
 * 
 * @param session The visualization session for emitting events
 * @param label Human-readable label for the mutex
 */
class VizMutex(
    private val session: VizSession,
    val label: String? = null
) {
    val mutexId: String = "mutex-${session.nextSeq()}"
    private val delegate = Mutex()
    
    // Wait queue tracking
    private val waitQueue = ConcurrentLinkedQueue<WaiterInfo>()
    private val holdStartTimes = ConcurrentHashMap<String, Long>()
    
    // Current owner tracking
    @Volatile
    private var currentOwnerId: String? = null
    @Volatile
    private var currentOwnerLabel: String? = null
    
    data class WaiterInfo(
        val coroutineId: String,
        val label: String?,
        val requestTime: Long
    )
    
    init {
        session.send(
            MutexCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                mutexId = mutexId,
                mutexLabel = label,
                ownerCoroutineId = null
            )
        )
    }
    
    /**
     * Check if the mutex is currently locked
     */
    val isLocked: Boolean get() = delegate.isLocked
    
    /**
     * Get the current owner coroutine ID (if any)
     */
    fun getOwnerId(): String? = currentOwnerId
    
    /**
     * Get the number of coroutines waiting for this mutex
     */
    fun getWaitQueueSize(): Int = waitQueue.size
    
    /**
     * Acquire the mutex lock (suspends if already held)
     */
    suspend fun lock(owner: Any? = null) {
        val coroutineElement = currentCoroutineContext()[VizCoroutineElement]
        val coroutineId = coroutineElement?.coroutineId ?: "unknown-${System.nanoTime()}"
        val coroutineLabel = coroutineElement?.label
        
        val wasLocked = delegate.isLocked
        val position = if (wasLocked) waitQueue.size + 1 else 0
        
        // Emit lock requested
        session.send(
            MutexLockRequested(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                mutexId = mutexId,
                mutexLabel = label,
                requesterId = coroutineId,
                requesterLabel = coroutineLabel,
                isLocked = wasLocked,
                queuePosition = position
            )
        )
        
        // Add to wait queue if locked
        val waiterInfo = WaiterInfo(coroutineId, coroutineLabel, System.nanoTime())
        if (wasLocked) {
            waitQueue.add(waiterInfo)
            emitQueueChanged()
        }
        
        // Actually acquire the lock (may suspend)
        val startWait = System.nanoTime()
        delegate.lock(owner)
        val waitDuration = System.nanoTime() - startWait
        
        // Remove from wait queue and update owner
        waitQueue.remove(waiterInfo)
        currentOwnerId = coroutineId
        currentOwnerLabel = coroutineLabel
        holdStartTimes[coroutineId] = System.nanoTime()
        
        // Emit lock acquired
        session.send(
            MutexLockAcquired(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                mutexId = mutexId,
                mutexLabel = label,
                acquirerId = coroutineId,
                acquirerLabel = coroutineLabel,
                waitDurationNanos = waitDuration
            )
        )
        
        if (wasLocked) {
            emitQueueChanged()
        }
    }
    
    /**
     * Release the mutex lock
     */
    fun unlock(owner: Any? = null) {
        val coroutineElement = try {
            // This might fail if called from non-coroutine context
            kotlinx.coroutines.runBlocking { currentCoroutineContext()[VizCoroutineElement] }
        } catch (_: Exception) { null }
        
        val coroutineId = coroutineElement?.coroutineId ?: currentOwnerId ?: "unknown"
        val coroutineLabel = coroutineElement?.label ?: currentOwnerLabel
        
        val holdStart = holdStartTimes.remove(coroutineId)
        val holdDuration = if (holdStart != null) System.nanoTime() - holdStart else 0L
        
        val nextWaiter = waitQueue.peek()
        
        // Clear owner before unlocking
        currentOwnerId = null
        currentOwnerLabel = null
        
        delegate.unlock(owner)
        
        // Emit unlocked
        session.send(
            MutexUnlocked(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                mutexId = mutexId,
                mutexLabel = label,
                releaserId = coroutineId,
                releaserLabel = coroutineLabel,
                nextWaiterId = nextWaiter?.coroutineId,
                holdDurationNanos = holdDuration
            )
        )
    }
    
    /**
     * Try to acquire the lock without suspending
     * 
     * @return true if lock was acquired, false if already held
     */
    fun tryLock(owner: Any? = null): Boolean {
        val coroutineElement = try {
            kotlinx.coroutines.runBlocking { currentCoroutineContext()[VizCoroutineElement] }
        } catch (_: Exception) { null }
        
        val coroutineId = coroutineElement?.coroutineId ?: "unknown-${System.nanoTime()}"
        val coroutineLabel = coroutineElement?.label
        
        val acquired = delegate.tryLock(owner)
        
        if (acquired) {
            currentOwnerId = coroutineId
            currentOwnerLabel = coroutineLabel
            holdStartTimes[coroutineId] = System.nanoTime()
            
            session.send(
                MutexLockAcquired(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    mutexId = mutexId,
                    mutexLabel = label,
                    acquirerId = coroutineId,
                    acquirerLabel = coroutineLabel,
                    waitDurationNanos = 0
                )
            )
        } else {
            session.send(
                MutexTryLockFailed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    mutexId = mutexId,
                    mutexLabel = label,
                    requesterId = coroutineId,
                    requesterLabel = coroutineLabel,
                    currentOwnerId = currentOwnerId
                )
            )
        }
        
        return acquired
    }
    
    /**
     * Execute block while holding the mutex lock
     */
    suspend fun <T> withLock(owner: Any? = null, action: suspend () -> T): T {
        lock(owner)
        try {
            return action()
        } finally {
            unlock(owner)
        }
    }
    
    private fun emitQueueChanged() {
        val waiters = waitQueue.toList()
        session.send(
            MutexQueueChanged(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                mutexId = mutexId,
                mutexLabel = label,
                waitingCoroutineIds = waiters.map { it.coroutineId },
                waitingLabels = waiters.map { it.label }
            )
        )
    }
}

// ============================================================================
// Extension function for creating VizMutex in VizScope
// ============================================================================

/**
 * Create a new instrumented Mutex within this VizScope
 * 
 * Example:
 * ```kotlin
 * val mutex = vizMutex("database-lock")
 * mutex.withLock {
 *     // Critical section
 * }
 * ```
 */
fun VizScope.vizMutex(label: String? = null): VizMutex {
    return VizMutex(session, label)
}

