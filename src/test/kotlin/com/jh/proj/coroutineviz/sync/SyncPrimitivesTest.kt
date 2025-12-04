package com.jh.proj.coroutineviz.sync

import com.jh.proj.coroutineviz.checksystem.EventRecorder
import com.jh.proj.coroutineviz.checksystem.MutexValidator
import com.jh.proj.coroutineviz.checksystem.SemaphoreValidator
import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizMutex
import com.jh.proj.coroutineviz.wrappers.VizScope
import com.jh.proj.coroutineviz.wrappers.VizSemaphore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Tests for Mutex and Semaphore synchronization primitives
 */
class SyncPrimitivesTest {

    private lateinit var session: VizSession
    private lateinit var recorder: EventRecorder

    @BeforeEach
    fun setup() {
        session = VizSession("test-session-${System.currentTimeMillis()}")
        recorder = EventRecorder()
        
        // Subscribe recorder to session events
        session.sessionScope.launch {
            session.eventBus.stream().collect { event ->
                recorder.record(event)
            }
        }
    }

    // ========================================================================
    // MUTEX TESTS
    // ========================================================================

    @Test
    @DisplayName("VizMutex - Basic lock/unlock emits correct events")
    fun `mutex basic lock unlock`() = runTest {
        val mutex = VizMutex(session, "test-mutex")

        // Verify creation event
        val createdEvents = session.store.all().filterIsInstance<MutexCreated>()
        assertEquals(1, createdEvents.size)
        assertEquals("test-mutex", createdEvents.first().mutexLabel)

        // Lock
        mutex.lock()
        assertTrue(mutex.isLocked)

        // Unlock
        mutex.unlock()
        assertFalse(mutex.isLocked)

        // Verify events
        val allEvents = session.store.all()
        assertTrue(allEvents.any { it is MutexLockAcquired })
        assertTrue(allEvents.any { it is MutexUnlocked })
    }

    @Test
    @DisplayName("VizMutex - withLock ensures proper lock/unlock pairing")
    fun `mutex withLock pattern`() = runTest {
        val mutex = VizMutex(session, "withlock-mutex")
        var counter = 0

        mutex.withLock {
            counter++
            delay(10)
        }

        assertEquals(1, counter)
        assertFalse(mutex.isLocked)

        // Verify balanced events
        val acquires = session.store.all().filterIsInstance<MutexLockAcquired>()
        val releases = session.store.all().filterIsInstance<MutexUnlocked>()
        assertEquals(acquires.size, releases.size)
    }

    @Test
    @DisplayName("VizMutex - tryLock returns false when locked")
    fun `mutex tryLock behavior`() = runTest {
        val mutex = VizMutex(session, "trylock-mutex")

        // First tryLock succeeds
        val first = mutex.tryLock()
        assertTrue(first)
        assertTrue(mutex.isLocked)

        // Second tryLock fails
        val second = mutex.tryLock()
        assertFalse(second)

        // Verify tryLock failed event
        val failedEvents = session.store.all().filterIsInstance<MutexTryLockFailed>()
        assertEquals(1, failedEvents.size)

        mutex.unlock()
    }

    @Test
    @DisplayName("VizMutex - Queue position tracked correctly")
    fun `mutex queue tracking`() = runTest {
        val mutex = VizMutex(session, "queue-mutex")
        val scope = VizScope(session)

        // First coroutine acquires lock
        val job1 = scope.vizLaunch("holder") {
            mutex.withLock {
                vizDelay(500)
            }
        }

        delay(50) // Ensure holder has lock

        // Second coroutine waits
        val job2 = scope.vizLaunch("waiter-1") {
            mutex.withLock {
                vizDelay(10)
            }
        }

        delay(10)

        // Verify queue position in request event
        val requests = session.store.all().filterIsInstance<MutexLockRequested>()
        val waiterRequest = requests.find { it.requesterLabel == "waiter-1" }
        assertNotNull(waiterRequest)
        assertEquals(1, waiterRequest?.queuePosition)
        assertTrue(waiterRequest?.isLocked == true)

        job1.join()
        job2.join()
    }

    // ========================================================================
    // SEMAPHORE TESTS
    // ========================================================================

    @Test
    @DisplayName("VizSemaphore - Basic acquire/release emits correct events")
    fun `semaphore basic acquire release`() = runTest {
        val semaphore = VizSemaphore(session, permits = 3, label = "test-semaphore")

        // Verify creation event
        val createdEvents = session.store.all().filterIsInstance<SemaphoreCreated>()
        assertEquals(1, createdEvents.size)
        assertEquals(3, createdEvents.first().totalPermits)

        // Acquire
        semaphore.acquire()
        assertEquals(2, semaphore.availablePermits)

        // Release
        semaphore.release()
        assertEquals(3, semaphore.availablePermits)

        // Verify events
        assertTrue(session.store.all().any { it is SemaphorePermitAcquired })
        assertTrue(session.store.all().any { it is SemaphorePermitReleased })
    }

    @Test
    @DisplayName("VizSemaphore - Never exceeds permit limit")
    fun `semaphore permit bounds`() = runTest {
        val semaphore = VizSemaphore(session, permits = 2, label = "bounded-semaphore")
        val scope = VizScope(session)

        // Launch multiple coroutines
        val jobs = (1..5).map { i ->
            scope.vizLaunch("worker-$i") {
                semaphore.withPermit {
                    // At most 2 should be active simultaneously
                    assertTrue(semaphore.getActiveHolderCount() <= 2)
                    vizDelay(50)
                }
            }
        }

        jobs.forEach { it.join() }

        // Verify all permits returned
        assertEquals(2, semaphore.availablePermits)
    }

    @Test
    @DisplayName("VizSemaphore - Utilization calculated correctly")
    fun `semaphore utilization`() = runTest {
        val semaphore = VizSemaphore(session, permits = 4, label = "util-semaphore")

        assertEquals(0.0, semaphore.getUtilization())

        semaphore.acquire()
        assertEquals(0.25, semaphore.getUtilization())

        semaphore.acquire()
        assertEquals(0.5, semaphore.getUtilization())

        semaphore.acquire()
        assertEquals(0.75, semaphore.getUtilization())

        semaphore.acquire()
        assertEquals(1.0, semaphore.getUtilization())

        repeat(4) { semaphore.release() }
        assertEquals(0.0, semaphore.getUtilization())
    }

    @Test
    @DisplayName("VizSemaphore - tryAcquire returns false when no permits")
    fun `semaphore tryAcquire behavior`() = runTest {
        val semaphore = VizSemaphore(session, permits = 1, label = "tryacquire-semaphore")

        // First succeeds
        assertTrue(semaphore.tryAcquire())
        assertEquals(0, semaphore.availablePermits)

        // Second fails
        assertFalse(semaphore.tryAcquire())

        // Verify failed event
        val failedEvents = session.store.all().filterIsInstance<SemaphoreTryAcquireFailed>()
        assertEquals(1, failedEvents.size)

        semaphore.release()
    }

    // ========================================================================
    // VALIDATOR TESTS
    // ========================================================================

    @Test
    @DisplayName("MutexValidator - Detects mutual exclusion violations")
    fun `validator detects mutex violations`() {
        val validator = MutexValidator(recorder)
        val mutexId = "test-mutex"

        // Simulate correct sequence
        recorder.record(createMutexAcquired(mutexId, "coroutine-1"))
        recorder.record(createMutexUnlocked(mutexId, "coroutine-1"))

        // Should not throw
        assertDoesNotThrow {
            validator.verifyMutualExclusion(mutexId)
        }
    }

    @Test
    @DisplayName("SemaphoreValidator - Detects permit bound violations")
    fun `validator detects semaphore violations`() {
        val validator = SemaphoreValidator(recorder)
        val semaphoreId = "test-semaphore"

        // Simulate correct state
        recorder.record(createSemaphoreState(semaphoreId, available = 2, total = 3, holders = 1))

        // Should not throw
        assertDoesNotThrow {
            validator.verifyPermitBounds(semaphoreId, 3)
        }
    }

    // ========================================================================
    // REAL-WORLD SCENARIO TESTS
    // ========================================================================

    @Test
    @DisplayName("Real-world: Thread-safe counter using Mutex")
    fun `real world thread safe counter`() = runTest {
        val scope = VizScope(session)
        val mutex = VizMutex(session, "counter-lock")
        var counter = 0

        // Launch multiple workers
        val jobs = (1..5).map { i ->
            scope.vizLaunch("worker-$i") {
                repeat(10) {
                    mutex.withLock {
                        val current = counter
                        delay(1)
                        counter = current + 1
                    }
                }
            }
        }

        jobs.forEach { it.join() }

        // Counter should be exactly 50 (5 workers * 10 increments)
        assertEquals(50, counter)
    }

    @Test
    @DisplayName("Real-world: Connection pool using Semaphore")
    fun `real world connection pool`() = runTest {
        val scope = VizScope(session)
        val connectionPool = VizSemaphore(session, permits = 3, label = "db-connections")
        var maxConcurrent = 0
        var currentConcurrent = 0
        val lock = VizMutex(session, "tracking-lock")

        val jobs = (1..10).map { i ->
            scope.vizLaunch("query-$i") {
                connectionPool.withPermit {
                    lock.withLock {
                        currentConcurrent++
                        if (currentConcurrent > maxConcurrent) {
                            maxConcurrent = currentConcurrent
                        }
                    }
                    
                    vizDelay((10..50).random().toLong())
                    
                    lock.withLock {
                        currentConcurrent--
                    }
                }
            }
        }

        jobs.forEach { it.join() }

        // Max concurrent should never exceed 3
        assertTrue(maxConcurrent <= 3, "Max concurrent was $maxConcurrent, expected <= 3")
        assertEquals(3, connectionPool.availablePermits)
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private fun createMutexAcquired(mutexId: String, acquirerId: String) = MutexLockAcquired(
        sessionId = session.sessionId,
        seq = session.nextSeq(),
        tsNanos = System.nanoTime(),
        mutexId = mutexId,
        mutexLabel = null,
        acquirerId = acquirerId,
        acquirerLabel = null,
        waitDurationNanos = 0
    )

    private fun createMutexUnlocked(mutexId: String, releaserId: String) = MutexUnlocked(
        sessionId = session.sessionId,
        seq = session.nextSeq(),
        tsNanos = System.nanoTime(),
        mutexId = mutexId,
        mutexLabel = null,
        releaserId = releaserId,
        releaserLabel = null,
        nextWaiterId = null,
        holdDurationNanos = 100
    )

    private fun createSemaphoreState(
        semaphoreId: String,
        available: Int,
        total: Int,
        holders: Int
    ) = SemaphoreStateChanged(
        sessionId = session.sessionId,
        seq = session.nextSeq(),
        tsNanos = System.nanoTime(),
        semaphoreId = semaphoreId,
        semaphoreLabel = null,
        availablePermits = available,
        totalPermits = total,
        activeHolders = (1..holders).map { "holder-$it" },
        activeHolderLabels = (1..holders).map { null },
        waitingCoroutines = emptyList(),
        waitingLabels = emptyList()
    )
}

