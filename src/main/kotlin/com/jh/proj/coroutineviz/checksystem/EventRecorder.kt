package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.VizEvent

class EventRecorder {
    private val events = mutableListOf<VizEvent>()
    private val eventsByCoroutine = mutableMapOf<String, MutableList<VizEvent>>()
    private val eventsByKind = mutableMapOf<String, MutableList<VizEvent>>()

    fun record(event: VizEvent) {
        events.add(event)

        if (event is CoroutineEvent) {
            eventsByCoroutine.getOrPut(event.coroutineId) { mutableListOf() }
                .add(event)
        }

        eventsByKind.getOrPut(event.kind, { mutableListOf() }).add(event)
    }

    fun all(): List<VizEvent> {
        return events.toList()
    }

    fun forCoroutine(coroutineId: String): List<VizEvent> = eventsByCoroutine.getOrElse(coroutineId, { emptyList() })
    fun ofKind(kind: String): List<VizEvent> = eventsByKind.getOrElse(kind, { emptyList() })
    fun inTimeRange(startTime: Long, endTime: Long): List<VizEvent> {
        return events.filter { it.tsNanos in startTime..endTime }
    }
}