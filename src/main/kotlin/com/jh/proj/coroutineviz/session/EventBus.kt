package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory

/**
 * Real-time event distribution bus using Kotlin Flow.
 *
 * The EventBus provides pub/sub functionality for [VizEvent] instances,
 * allowing multiple subscribers to receive events as they occur. It uses
 * a [MutableSharedFlow] with a large buffer (10,000 events) to handle
 * bursts of events without blocking emitters.
 *
 * If the buffer overflows, oldest events are dropped to prevent backpressure
 * from blocking event emission. This trade-off prioritizes real-time
 * responsiveness over guaranteed delivery.
 *
 * Usage:
 * ```kotlin
 * // Subscribe to events
 * eventBus.stream().collect { event ->
 *     println("Received: ${event.kind}")
 * }
 *
 * // Emit events (non-blocking)
 * eventBus.send(event)
 * ```
 */
class EventBus {
    private val logger = LoggerFactory.getLogger(EventBus::class.java)

    private val flow = MutableSharedFlow<VizEvent>(
        extraBufferCapacity = 10_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Non-suspending event emission.
     * Returns true if event was emitted, false if buffer is full (rare).
     */
    fun send(event: VizEvent): Boolean {
        val emitted = flow.tryEmit(event)
        if (!emitted) {
            logger.error("Event buffer full! Dropped event: ${event.kind} (seq=${event.seq})")
        }
        return emitted
    }

    /**
     * Suspending version for compatibility.
     * Use this if you need backpressure handling.
     */
    suspend fun sendSuspend(event: VizEvent) {
        flow.emit(event)
    }

    /**
     * Get a Flow to subscribe to events.
     *
     * @return Cold flow that emits all future events
     */
    fun stream(): Flow<VizEvent> = flow.asSharedFlow()
}