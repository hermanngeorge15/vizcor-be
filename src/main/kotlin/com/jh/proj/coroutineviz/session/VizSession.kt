package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.VizEvent
import com.jh.proj.coroutineviz.models.RuntimeSnapshot
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Central container for a visualization session.
 *
 * A VizSession holds all the state and infrastructure needed to track coroutine
 * execution within a single visualization context. Each session maintains:
 * - An [EventBus] for real-time event distribution to subscribers
 * - An [EventStore] for persistent storage of all events
 * - A [RuntimeSnapshot] reflecting current coroutine states
 * - A [ProjectionService] for computing derived views (timelines, hierarchies)
 *
 * Events flow through the session in the following order:
 * 1. Event is appended to [store] (persistent log)
 * 2. Event is applied to [snapshot] via [EventApplier] (state update)
 * 3. Event is broadcast via [eventBus] (real-time notifications)
 *
 * @property sessionId Unique identifier for this session
 */
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
