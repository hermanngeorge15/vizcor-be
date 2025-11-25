package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.VizEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory

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

    fun stream(): Flow<VizEvent> = flow.asSharedFlow()
}