package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.VizEvent

/**
 * Verifies happens-before relationships between events.
 *
 * Use cases:
 * - A must happen before B (partial order)
 * - Chain: A < B < C < D
 * - Group A happens before Group B
 * - Time window constraints
 *
 * Usage:
 * ```
 * val checker = HappensBeforeChecker(recorder)
 *
 * // Simple: A before B
 * checker.checkHappensBefore(
 *     EventSelector.labeled("child", "CoroutineFailed"),
 *     EventSelector.labeled("parent", "CoroutineCancelled")
 * ).assertSuccess()
 *
 * // Chain: A < B < C
 * checker.checkChain(
 *     EventSelector.labeled("child-2", "CoroutineFailed"),
 *     EventSelector.labeled("parent", "CoroutineCancelled"),
 *     EventSelector.labeled("child-1", "CoroutineCancelled")
 * ).assertSuccess()
 * ```
 */
class HappensBeforeChecker(private val recorder: EventRecorder) {

    /**
     * Verifies eventA happens before eventB (A.seq < B.seq).
     */
    fun checkHappensBefore(
        eventA: EventSelector,
        eventB: EventSelector
    ): OrderResult {
        val a = recorder.find(eventA)
        val b = recorder.find(eventB)

        return when {
            a == null -> OrderResult.Failure(
                message = "Event A not found",
                context = mapOf("selector" to eventA.toString())
            )
            b == null -> OrderResult.Failure(
                message = "Event B not found",
                context = mapOf("selector" to eventB.toString())
            )
            a.seq < b.seq -> OrderResult.Success
            else -> OrderResult.Failure(
                message = "Event A should happen before Event B",
                expected = "A.seq < B.seq",
                actual = "A.seq=${a.seq}, B.seq=${b.seq}",
                context = mapOf(
                    "eventA" to formatEvent(a),
                    "eventB" to formatEvent(b),
                    "timeDelta" to "${b.tsNanos - a.tsNanos}ns"
                )
            )
        }
    }

    /**
     * Verifies a chain of happens-before relationships: A < B < C < ... < Z
     */
    fun checkChain(vararg selectors: EventSelector): OrderResult {
        if (selectors.size < 2) {
            return OrderResult.Failure("Chain must have at least 2 events")
        }

        val found = selectors.map { selector ->
            recorder.find(selector) to selector
        }

        // Check all found
        found.forEachIndexed { i, (event, selector) ->
            if (event == null) {
                return OrderResult.Failure(
                    message = "Event not found in chain at position $i",
                    context = mapOf(
                        "position" to i,
                        "selector" to selector.toString()
                    )
                )
            }
        }

        // Check ordering
        for (i in 0 until found.size - 1) {
            val (a, _) = found[i]
            val (b, _) = found[i + 1]

            if (a!!.seq >= b!!.seq) {
                return OrderResult.Failure(
                    message = "Chain broken between position $i and ${i + 1}",
                    expected = "seq[$i] < seq[${i + 1}]",
                    actual = "seq[$i]=${a.seq}, seq[${i + 1}]=${b.seq}",
                    context = mapOf(
                        "event[$i]" to formatEvent(a),
                        "event[${i + 1}]" to formatEvent(b),
                        "timeDelta" to "${b.tsNanos - a.tsNanos}ns"
                    )
                )
            }
        }

        return OrderResult.Success
    }

    /**
     * Verifies all events in groupA happen before any event in groupB.
     */
    fun checkGroupBefore(
        groupA: List<EventSelector>,
        groupB: List<EventSelector>
    ): OrderResult {
        val eventsA = groupA.mapNotNull { recorder.find(it) }
        val eventsB = groupB.mapNotNull { recorder.find(it) }

        if (eventsA.isEmpty()) {
            return OrderResult.Failure(
                message = "No events found for group A",
                context = mapOf("groupASize" to groupA.size)
            )
        }
        if (eventsB.isEmpty()) {
            return OrderResult.Failure(
                message = "No events found for group B",
                context = mapOf("groupBSize" to groupB.size)
            )
        }

        val maxSeqA = eventsA.maxOf { it.seq }
        val minSeqB = eventsB.minOf { it.seq }

        return if (maxSeqA < minSeqB) {
            OrderResult.Success
        } else {
            val latestA = eventsA.maxByOrNull { it.seq }!!
            val earliestB = eventsB.minByOrNull { it.seq }!!

            OrderResult.Failure(
                message = "Group A should complete before Group B starts",
                expected = "max(A.seq) < min(B.seq)",
                actual = "max(A.seq)=$maxSeqA, min(B.seq)=$minSeqB",
                context = mapOf(
                    "latestInA" to formatEvent(latestA),
                    "earliestInB" to formatEvent(earliestB)
                )
            )
        }
    }

    /**
     * Verifies eventB happens within maxNanos of eventA.
     */
    fun checkWithinWindow(
        eventA: EventSelector,
        eventB: EventSelector,
        maxNanos: Long
    ): OrderResult {
        val a = recorder.find(eventA)
        val b = recorder.find(eventB)

        return when {
            a == null -> OrderResult.Failure("Event A not found")
            b == null -> OrderResult.Failure("Event B not found")
            else -> {
                val delta = b.tsNanos - a.tsNanos
                if (delta in 0..maxNanos) {
                    OrderResult.Success
                } else {
                    OrderResult.Failure(
                        message = "Event B occurred outside time window after Event A",
                        expected = "0 <= delta <= ${maxNanos}ns",
                        actual = "delta = ${delta}ns",
                        context = mapOf(
                            "eventA" to formatEvent(a),
                            "eventB" to formatEvent(b),
                            "deltaMs" to "${delta / 1_000_000}ms"
                        )
                    )
                }
            }
        }
    }

    /**
     * Verifies events happen in order across different coroutines.
     * Useful for validating inter-coroutine communication.
     */
    fun checkCrossCoroutineOrder(
        first: Pair<String, String>,  // (label, kind)
        second: Pair<String, String>  // (label, kind)
    ): OrderResult {
        return checkHappensBefore(
            EventSelector.labeled(first.first, first.second),
            EventSelector.labeled(second.first, second.second)
        )
    }

    private fun formatEvent(event: VizEvent): String {
        val label = if (event is CoroutineEvent) event.label else null
        return "${event.kind} [seq=${event.seq}, label=$label]"
    }
}

