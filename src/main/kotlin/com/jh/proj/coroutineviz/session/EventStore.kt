package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.VizEvent
import java.util.concurrent.CopyOnWriteArrayList

class EventStore {
    private val events = CopyOnWriteArrayList<VizEvent>()

    fun append(event: VizEvent) {
        events.add(event)
    }

    fun all(): List<VizEvent> = events
}