package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.VizEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Append-only event log for persisting visualization events.
 *
 * The EventStore provides durable storage of all events emitted during
 * a session. Events are stored in emission order and can be replayed
 * to reconstruct state or for debugging purposes.
 *
 * This implementation uses [CopyOnWriteArrayList] for thread-safe
 * concurrent access. For production use with large event volumes,
 * consider replacing with a persistent store (database, file, etc.).
 *
 * Usage:
 * ```kotlin
 * store.append(event)
 * val allEvents = store.all()
 * ```
 */
class EventStore {
    private val events = CopyOnWriteArrayList<VizEvent>()

    /**
     * Append an event to the store.
     *
     * @param event The event to store
     */
    fun append(event: VizEvent) {
        events.add(event)
    }

    /**
     * Retrieve all stored events in emission order.
     *
     * @return Immutable view of all events
     */
    fun all(): List<VizEvent> = events
}