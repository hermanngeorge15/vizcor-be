package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Instrumented Semaphore wrapper that emits visualization events.
 * 
 * This wrapper provides full transparency into semaphore behavior:
 * - Permit acquisition and release timing
 * - Active holder tracking
 * - Wait queue management
 * - Utilization metrics
 * 
 * Real-world use cases:
 * - Connection pool limiting (max N connections)
 * - Rate limiting (max N concurrent API calls)
 * - Resource throttling
 * - Batch processing control
 * 
 * @param session The visualization session for emitting events
 * @param permits Number of permits (concurrent access limit)
 * @param label Human-readable label for the semaphore
 */
class VizSemaphore(
    private val session: VizSession,
    val permits: Int,
    val label: String? = null
) {
    val semaphoreId: String = "semaphore-${session.nextSeq()}"
    private val delegate = Semaphore(permits)
    private val totalPermits = permits
    
    // Active holders tracking
    private val activeHolders = ConcurrentHashMap<String, HolderInfo>()
    
    // Wait queue tracking
    private val waitQueue = ConcurrentLinkedQueue<WaiterInfo>()
    
    data class HolderInfo(
        val coroutineId: String,
        val label: String?,
        val acquireTime: Long
    )
    
    data class WaiterInfo(
        val coroutineId: String,
        val label: String?,
        val requestTime: Long,
        val permitsRequested: Int
    )
    
    init {
        session.send(
            SemaphoreCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                semaphoreId = semaphoreId,
                semaphoreLabel = label,
                totalPermits = totalPermits
            )
        )
    }
    
    /**
     * Get number of available permits
     */
    val availablePermits: Int get() = delegate.availablePermits
    
    /**
     * Get number of active permit holders
     */
    fun getActiveHolderCount(): Int = activeHolders.size
    
    /**
     * Get number of coroutines waiting for permits
     */
    fun getWaitQueueSize(): Int = waitQueue.size
    
    /**
     * Calculate current utilization (0.0 to 1.0)
     */
    fun getUtilization(): Double {
        return (totalPermits - availablePermits).toDouble() / totalPermits
    }
    
    /**
     * Acquire a permit (suspends if none available)
     */
    suspend fun acquire() {
        val coroutineElement = currentCoroutineContext()[VizCoroutineElement]
        val coroutineId = coroutineElement?.coroutineId ?: "unknown-${System.nanoTime()}"
        val coroutineLabel = coroutineElement?.label
        
        val available = delegate.availablePermits
        
        // Emit acquire requested
        session.send(
            SemaphoreAcquireRequested(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                semaphoreId = semaphoreId,
                semaphoreLabel = label,
                requesterId = coroutineId,
                requesterLabel = coroutineLabel,
                availablePermits = available,
                permitsRequested = 1
            )
        )
        
        // Add to wait queue if no permits available
        val waiterInfo = WaiterInfo(coroutineId, coroutineLabel, System.nanoTime(), 1)
        if (available == 0) {
            waitQueue.add(waiterInfo)
            emitStateChanged()
        }
        
        // Actually acquire the permit (may suspend)
        val startWait = System.nanoTime()
        delegate.acquire()
        val waitDuration = System.nanoTime() - startWait
        
        // Update tracking
        waitQueue.remove(waiterInfo)
        activeHolders[coroutineId] = HolderInfo(coroutineId, coroutineLabel, System.nanoTime())
        
        // Emit permit acquired
        session.send(
            SemaphorePermitAcquired(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                semaphoreId = semaphoreId,
                semaphoreLabel = label,
                acquirerId = coroutineId,
                acquirerLabel = coroutineLabel,
                remainingPermits = delegate.availablePermits,
                waitDurationNanos = waitDuration
            )
        )
        
        emitStateChanged()
    }
    
    /**
     * Release a permit back to the pool
     */
    fun release() {
        val coroutineElement = try {
            kotlinx.coroutines.runBlocking { currentCoroutineContext()[VizCoroutineElement] }
        } catch (_: Exception) { null }
        
        val coroutineId = coroutineElement?.coroutineId 
            ?: activeHolders.keys.firstOrNull() 
            ?: "unknown"
        val coroutineLabel = coroutineElement?.label
        
        val holderInfo = activeHolders.remove(coroutineId)
        val holdDuration = if (holderInfo != null) {
            System.nanoTime() - holderInfo.acquireTime
        } else 0L
        
        delegate.release()
        
        // Emit permit released
        session.send(
            SemaphorePermitReleased(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                semaphoreId = semaphoreId,
                semaphoreLabel = label,
                releaserId = coroutineId,
                releaserLabel = coroutineLabel,
                newAvailablePermits = delegate.availablePermits,
                holdDurationNanos = holdDuration
            )
        )
        
        emitStateChanged()
    }
    
    /**
     * Try to acquire a permit without suspending
     * 
     * @return true if permit was acquired, false if none available
     */
    fun tryAcquire(): Boolean {
        val coroutineElement = try {
            kotlinx.coroutines.runBlocking { currentCoroutineContext()[VizCoroutineElement] }
        } catch (_: Exception) { null }
        
        val coroutineId = coroutineElement?.coroutineId ?: "unknown-${System.nanoTime()}"
        val coroutineLabel = coroutineElement?.label
        
        val acquired = delegate.tryAcquire()
        
        if (acquired) {
            activeHolders[coroutineId] = HolderInfo(coroutineId, coroutineLabel, System.nanoTime())
            
            session.send(
                SemaphorePermitAcquired(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    semaphoreId = semaphoreId,
                    semaphoreLabel = label,
                    acquirerId = coroutineId,
                    acquirerLabel = coroutineLabel,
                    remainingPermits = delegate.availablePermits,
                    waitDurationNanos = 0
                )
            )
            
            emitStateChanged()
        } else {
            session.send(
                SemaphoreTryAcquireFailed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    semaphoreId = semaphoreId,
                    semaphoreLabel = label,
                    requesterId = coroutineId,
                    requesterLabel = coroutineLabel,
                    availablePermits = delegate.availablePermits,
                    permitsRequested = 1
                )
            )
        }
        
        return acquired
    }
    
    /**
     * Execute block while holding a permit
     */
    suspend fun <T> withPermit(action: suspend () -> T): T {
        acquire()
        try {
            return action()
        } finally {
            release()
        }
    }
    
    private fun emitStateChanged() {
        val holders = activeHolders.values.toList()
        val waiters = waitQueue.toList()
        
        session.send(
            SemaphoreStateChanged(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                semaphoreId = semaphoreId,
                semaphoreLabel = label,
                availablePermits = delegate.availablePermits,
                totalPermits = totalPermits,
                activeHolders = holders.map { it.coroutineId },
                activeHolderLabels = holders.map { it.label },
                waitingCoroutines = waiters.map { it.coroutineId },
                waitingLabels = waiters.map { it.label }
            )
        )
    }
}

// ============================================================================
// Extension function for creating VizSemaphore in VizScope
// ============================================================================

/**
 * Create a new instrumented Semaphore within this VizScope
 * 
 * Example:
 * ```kotlin
 * val connectionPool = vizSemaphore("db-connections", permits = 3)
 * connectionPool.withPermit {
 *     // Use connection
 * }
 * ```
 */
fun VizScope.vizSemaphore(label: String? = null, permits: Int): VizSemaphore {
    return VizSemaphore(session, permits, label)
}

