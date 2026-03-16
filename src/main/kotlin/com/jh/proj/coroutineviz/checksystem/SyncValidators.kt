package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.*

/**
 * Validator for Mutex synchronization patterns.
 * 
 * Provides assertions for:
 * - Mutual exclusion verification
 * - No deadlock conditions
 * - Fairness (FIFO ordering)
 * - Lock/unlock pairing
 */
class MutexValidator(private val recorder: EventRecorder) {
    
    /**
     * Verify that no deadlocks were detected during execution
     */
    fun verifyNoDeadlock() {
        val deadlocks = recorder.ofKind("DeadlockDetected")
        if (deadlocks.isNotEmpty()) {
            val deadlock = deadlocks.first() as DeadlockDetected
            throw AssertionError(
                "Deadlock detected involving coroutines: ${deadlock.involvedCoroutines}\n" +
                "Cycle: ${deadlock.cycleDescription}"
            )
        }
    }
    
    /**
     * Verify that only one coroutine held the mutex at any time
     */
    fun verifyMutualExclusion(mutexId: String) {
        val events = recorder.all().filter { 
            it is MutexEvent && (it as MutexEvent).mutexId == mutexId 
        }
        
        var currentHolder: String? = null
        
        for (event in events) {
            when (event) {
                is MutexLockAcquired -> {
                    if (currentHolder != null) {
                        throw AssertionError(
                            "Mutex $mutexId held by $currentHolder when ${event.acquirerId} acquired it"
                        )
                    }
                    currentHolder = event.acquirerId
                }
                is MutexUnlocked -> {
                    if (currentHolder != event.releaserId) {
                        throw AssertionError(
                            "Mutex $mutexId released by ${event.releaserId} but held by $currentHolder"
                        )
                    }
                    currentHolder = null
                }
                else -> {}
            }
        }
    }
    
    /**
     * Verify FIFO ordering for queued lock requests
     */
    fun verifyFairness(mutexId: String) {
        val requests = recorder.ofKind("MutexLockRequested")
            .filterIsInstance<MutexLockRequested>()
            .filter { it.mutexId == mutexId && it.queuePosition > 0 }
            .sortedBy { it.seq }
        
        val acquisitions = recorder.ofKind("MutexLockAcquired")
            .filterIsInstance<MutexLockAcquired>()
            .filter { it.mutexId == mutexId && it.waitDurationNanos > 0 }
            .sortedBy { it.seq }
        
        // The acquisition order should match request order for queued requests
        val requestOrder = requests.map { it.requesterId }
        val acquisitionOrder = acquisitions.map { it.acquirerId }
        
        for (i in requestOrder.indices) {
            if (i < acquisitionOrder.size && requestOrder[i] != acquisitionOrder[i]) {
                throw AssertionError(
                    "Mutex $mutexId fairness violation: " +
                    "Request order: $requestOrder, Acquisition order: $acquisitionOrder"
                )
            }
        }
    }
    
    /**
     * Verify all locks are eventually unlocked
     */
    fun verifyNoLockLeaks(mutexId: String) {
        val acquireCount = recorder.ofKind("MutexLockAcquired")
            .filterIsInstance<MutexLockAcquired>()
            .count { it.mutexId == mutexId }
        
        val releaseCount = recorder.ofKind("MutexUnlocked")
            .filterIsInstance<MutexUnlocked>()
            .count { it.mutexId == mutexId }
        
        if (acquireCount != releaseCount) {
            throw AssertionError(
                "Mutex $mutexId lock leak: $acquireCount acquires but only $releaseCount releases"
            )
        }
    }
    
    /**
     * Get contention statistics for a mutex
     */
    fun getContentionStats(mutexId: String): MutexContentionStats {
        val requests = recorder.ofKind("MutexLockRequested")
            .filterIsInstance<MutexLockRequested>()
            .filter { it.mutexId == mutexId }
        
        val acquisitions = recorder.ofKind("MutexLockAcquired")
            .filterIsInstance<MutexLockAcquired>()
            .filter { it.mutexId == mutexId }
        
        val unlocks = recorder.ofKind("MutexUnlocked")
            .filterIsInstance<MutexUnlocked>()
            .filter { it.mutexId == mutexId }
        
        val contentionRequests = requests.count { it.isLocked }
        val waitTimes = acquisitions.map { it.waitDurationNanos }
        val holdTimes = unlocks.map { it.holdDurationNanos }
        
        return MutexContentionStats(
            totalRequests = requests.size,
            contentionRequests = contentionRequests,
            contentionRate = if (requests.isNotEmpty()) contentionRequests.toDouble() / requests.size else 0.0,
            avgWaitTimeNanos = waitTimes.average().takeIf { !it.isNaN() } ?: 0.0,
            maxWaitTimeNanos = waitTimes.maxOrNull() ?: 0,
            avgHoldTimeNanos = holdTimes.average().takeIf { !it.isNaN() } ?: 0.0,
            maxHoldTimeNanos = holdTimes.maxOrNull() ?: 0
        )
    }
}

data class MutexContentionStats(
    val totalRequests: Int,
    val contentionRequests: Int,
    val contentionRate: Double,
    val avgWaitTimeNanos: Double,
    val maxWaitTimeNanos: Long,
    val avgHoldTimeNanos: Double,
    val maxHoldTimeNanos: Long
)

/**
 * Validator for Semaphore synchronization patterns.
 * 
 * Provides assertions for:
 * - Permit bound verification
 * - No permit leaks
 * - Utilization metrics
 */
class SemaphoreValidator(private val recorder: EventRecorder) {
    
    /**
     * Verify the semaphore never exceeds its permit limit
     */
    fun verifyPermitBounds(semaphoreId: String, maxPermits: Int) {
        val stateChanges = recorder.ofKind("SemaphoreStateChanged")
            .filterIsInstance<SemaphoreStateChanged>()
            .filter { it.semaphoreId == semaphoreId }
        
        for (state in stateChanges) {
            if (state.activeHolders.size > maxPermits) {
                throw AssertionError(
                    "Semaphore $semaphoreId permit limit exceeded: " +
                    "${state.activeHolders.size} holders but only $maxPermits permits"
                )
            }
            if (state.availablePermits < 0) {
                throw AssertionError(
                    "Semaphore $semaphoreId has negative permits: ${state.availablePermits}"
                )
            }
            if (state.availablePermits > state.totalPermits) {
                throw AssertionError(
                    "Semaphore $semaphoreId has more permits than total: " +
                    "${state.availablePermits} > ${state.totalPermits}"
                )
            }
        }
    }
    
    /**
     * Verify all permits are eventually released
     */
    fun verifyNoPermitLeaks(semaphoreId: String, totalPermits: Int) {
        val finalState = recorder.ofKind("SemaphoreStateChanged")
            .filterIsInstance<SemaphoreStateChanged>()
            .filter { it.semaphoreId == semaphoreId }
            .lastOrNull()
        
        if (finalState != null && finalState.availablePermits != totalPermits) {
            throw AssertionError(
                "Semaphore $semaphoreId permit leak: " +
                "only ${finalState.availablePermits}/$totalPermits permits available at end"
            )
        }
    }
    
    /**
     * Verify acquire/release counts match
     */
    fun verifyBalancedOperations(semaphoreId: String) {
        val acquireCount = recorder.ofKind("SemaphorePermitAcquired")
            .filterIsInstance<SemaphorePermitAcquired>()
            .count { it.semaphoreId == semaphoreId }
        
        val releaseCount = recorder.ofKind("SemaphorePermitReleased")
            .filterIsInstance<SemaphorePermitReleased>()
            .count { it.semaphoreId == semaphoreId }
        
        if (acquireCount != releaseCount) {
            throw AssertionError(
                "Semaphore $semaphoreId unbalanced operations: " +
                "$acquireCount acquires but $releaseCount releases"
            )
        }
    }
    
    /**
     * Get utilization statistics for a semaphore
     */
    fun getUtilizationStats(semaphoreId: String): SemaphoreUtilizationStats {
        val stateChanges = recorder.ofKind("SemaphoreStateChanged")
            .filterIsInstance<SemaphoreStateChanged>()
            .filter { it.semaphoreId == semaphoreId }
        
        val acquires = recorder.ofKind("SemaphorePermitAcquired")
            .filterIsInstance<SemaphorePermitAcquired>()
            .filter { it.semaphoreId == semaphoreId }
        
        val releases = recorder.ofKind("SemaphorePermitReleased")
            .filterIsInstance<SemaphorePermitReleased>()
            .filter { it.semaphoreId == semaphoreId }
        
        val utilizationSamples = stateChanges.map { state ->
            (state.totalPermits - state.availablePermits).toDouble() / state.totalPermits
        }
        
        val waitTimes = acquires.map { it.waitDurationNanos }
        val holdTimes = releases.map { it.holdDurationNanos }
        
        val maxConcurrent = stateChanges.maxOfOrNull { it.activeHolders.size } ?: 0
        val maxWaiting = stateChanges.maxOfOrNull { it.waitingCoroutines.size } ?: 0
        
        return SemaphoreUtilizationStats(
            totalAcquires = acquires.size,
            totalReleases = releases.size,
            avgUtilization = utilizationSamples.average().takeIf { !it.isNaN() } ?: 0.0,
            maxUtilization = utilizationSamples.maxOrNull() ?: 0.0,
            maxConcurrentHolders = maxConcurrent,
            maxWaitingQueue = maxWaiting,
            avgWaitTimeNanos = waitTimes.average().takeIf { !it.isNaN() } ?: 0.0,
            maxWaitTimeNanos = waitTimes.maxOrNull() ?: 0,
            avgHoldTimeNanos = holdTimes.average().takeIf { !it.isNaN() } ?: 0.0,
            maxHoldTimeNanos = holdTimes.maxOrNull() ?: 0
        )
    }
}

data class SemaphoreUtilizationStats(
    val totalAcquires: Int,
    val totalReleases: Int,
    val avgUtilization: Double,
    val maxUtilization: Double,
    val maxConcurrentHolders: Int,
    val maxWaitingQueue: Int,
    val avgWaitTimeNanos: Double,
    val maxWaitTimeNanos: Long,
    val avgHoldTimeNanos: Double,
    val maxHoldTimeNanos: Long
)

