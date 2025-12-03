package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.VizEvent

data class EventSelector(
    val label: String? = null,
    val kind: String? = null,
    val coroutineId: String? = null,
    val predicate: ((VizEvent) -> Boolean)? = null
) {
    /**
     * Checks if an event matches all specified criteria.
     */
    fun matches(event: VizEvent): Boolean {
        // Check label
        if (label != null) {
            if (event !is CoroutineEvent || event.label != label) {
                return false
            }
        }

        // Check kind
        if (kind != null && event.kind != kind) {
            return false
        }

        // Check coroutineId
        if (coroutineId != null) {
            if (event !is CoroutineEvent || event.coroutineId != coroutineId) {
                return false
            }
        }

        // Check custom predicate
        if (predicate != null && !predicate(event)) {
            return false
        }

        return true
    }

    companion object {
        /**
         * Select by label and optionally by kind.
         */
        fun labeled(label: String, kind: String? = null) =
            EventSelector(label = label, kind = kind)

        /**
         * Select by coroutine ID and optionally by kind.
         */
        fun byId(coroutineId: String, kind: String? = null) =
            EventSelector(coroutineId = coroutineId, kind = kind)

        /**
         * Select by event kind only.
         */
        fun ofKind(kind: String) =
            EventSelector(kind = kind)

        /**
         * Select by custom predicate.
         */
        fun matching(predicate: (VizEvent) -> Boolean) =
            EventSelector(predicate = predicate)

        /**
         * Select terminal events (Completed, Failed, or Cancelled).
         */
        fun terminal(label: String) =
            EventSelector(label = label, predicate = { event ->
                event.kind in setOf("CoroutineCompleted", "CoroutineFailed", "CoroutineCancelled")
            })
    }

    override fun toString(): String = buildString {
        append("EventSelector(")
        val parts = mutableListOf<String>()
        label?.let { parts.add("label='$it'") }
        kind?.let { parts.add("kind='$it'") }
        coroutineId?.let { parts.add("coroutineId='$it'") }
        if (predicate != null) parts.add("predicate=<custom>")
        append(parts.joinToString(", "))
        append(")")
    }}