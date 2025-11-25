package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class VizSession(
    val sessionId: String
) {
    // Session-scoped coroutine scope for async operations
    val sessionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    val eventBus = EventBus()
    val bus = eventBus  // Alias for backwards compatibility
    val store = EventStore()
    val snapshot = RuntimeSnapshot()
    private val applier = EventApplier(snapshot)

    // Sequence generator to keep events globally ordered in this session
    private val seqGenerator = AtomicLong(0)

    val projectionService = ProjectionService(this)

    /**
     * Allocate next sequence number.
     */
    fun nextSeq(): Long = seqGenerator.incrementAndGet()

    /**
     * Non-suspending event send - PREFERRED for most use cases.
     * Synchronously emits event to bus, stores it, and updates snapshot.
     */
    fun send(event: VizEvent) {
        store.append(event)
        applier.apply(event)
        eventBus.send(event)
    }

    /**
     * Async event send - for non-coroutine contexts.
     * Launches on session scope to emit event asynchronously.
     */
    fun sendAsync(event: VizEvent) {
        sessionScope.launch {
            send(event)
        }
    }

    /**
     * Suspending version - for backwards compatibility.
     * Delegates to synchronous send() since bus.send() doesn't suspend.
     */
    fun sent(event: VizEvent) {
        send(event)
    }

    /**
     * Clean up session resources.
     * Call this when session is closed.
     */
    fun close() {
        sessionScope.cancel()
    }
}