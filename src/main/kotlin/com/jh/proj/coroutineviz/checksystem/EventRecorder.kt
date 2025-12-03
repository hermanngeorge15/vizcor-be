package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.VizEvent
import java.util.concurrent.*

class EventRecorder {

    private val events = CopyOnWriteArrayList<VizEvent>()
    private val eventsByCoroutine = ConcurrentHashMap<String, CopyOnWriteArrayList<VizEvent>>()
    private val eventsByLabel = ConcurrentHashMap<String, CopyOnWriteArrayList<VizEvent>>()
    private val eventsByKind = ConcurrentHashMap<String, CopyOnWriteArrayList<VizEvent>>()

    fun record(event: VizEvent) {
        events.add(event)

        if (event is CoroutineEvent) {
            eventsByCoroutine.getOrPut(event.coroutineId) { CopyOnWriteArrayList<VizEvent>() }
                .add(event)

            event.label?.let { label ->
                eventsByLabel.getOrPut(label) { CopyOnWriteArrayList<VizEvent>() }.add(event)
            }
        }

        eventsByKind.getOrPut(event.kind) { CopyOnWriteArrayList<VizEvent>() }.add(event)
    }

    /**
     * Returns all recorded events in order.
     */
    fun all(): List<VizEvent> = events.toList()

    /**
     * Returns events for a specific coroutine ID.
     */
    fun forCoroutine(coroutineId: String): List<VizEvent> =
        eventsByCoroutine[coroutineId]?.toList() ?: emptyList()

    /**
     * Returns events for a specific coroutine label.
     */
    fun forLabel(label: String): List<VizEvent> =
        eventsByLabel[label]?.toList() ?: emptyList()

    /**
     * Returns events of a specific kind.
     */
    fun ofKind(kind: String): List<VizEvent> =
        eventsByKind[kind]?.toList() ?: emptyList()

    /**
     * Returns events within a time range (inclusive).
     */
    fun inTimeRange(startNanos: Long, endNanos: Long): List<VizEvent> =
        events.filter { it.tsNanos in startNanos..endNanos }

    /**
     * Finds first event matching the selector.
     */
    fun find(selector: EventSelector): VizEvent? =
        events.find { selector.matches(it) }

    /**
     * Finds all events matching the selector.
     */
    fun findAll(selector: EventSelector): List<VizEvent> =
        events.filter { selector.matches(it) }

    /**
     * Finds first event matching any of the given selectors.
     */
    fun findAnyOf(vararg selectors: EventSelector): VizEvent? =
        selectors.firstNotNullOfOrNull { selector ->
            events.find { selector.matches(it) }
        }

    /**
     * Clears all recorded events and indices.
     */
    fun clear() {
        events.clear()
        eventsByCoroutine.clear()
        eventsByLabel.clear()
        eventsByKind.clear()
    }

    /**
     * Returns a summary of recorded events for debugging.
     */
    fun summary(): String = """
        EventRecorder Summary:
        - Total events: ${events.size}
        - Events by kind: ${eventsByKind.mapValues { it.value.size }}
        - Coroutines tracked: ${eventsByCoroutine.size}
        - Labels tracked: ${eventsByLabel.size}
    """.trimIndent()

    /**
     * Returns event count statistics.
     */
    fun stats(): EventStats = EventStats(
        totalEvents = events.size,
        byKind = eventsByKind.mapValues { it.value.size },
        coroutineCount = eventsByCoroutine.size,
        labelCount = eventsByLabel.size
    )
}
