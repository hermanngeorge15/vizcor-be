package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.VizEvent

/**
 * Analyzes timing and performance of coroutine execution.
 *
 * Provides:
 * - Duration measurements
 * - Timing comparisons
 * - Statistical analysis
 * - Performance validation
 *
 * Usage:
 * ```
 * val analyzer = TimingAnalyzer(recorder)
 * analyzer.validateDuration("worker", 100, 500).assertSuccess()
 * analyzer.validateCompletedBefore("fast", "slow").assertSuccess()
 * val stats = analyzer.getTimingStats("parent")
 * ```
 */
class TimingAnalyzer(private val recorder: EventRecorder) {

    /**
     * Validates that a coroutine's execution duration is within expected range.
     * Duration is from Created to terminal state (Completed/Failed/Cancelled).
     */
    fun validateDuration(
        label: String,
        minMs: Long,
        maxMs: Long
    ): OrderResult {
        val duration = getDurationMs(label)
            ?: return OrderResult.Failure(
                message = "Could not calculate duration for '$label'",
                context = mapOf("label" to label)
            )

        return if (duration in minMs..maxMs) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Duration outside expected range for '$label'",
                expected = "${minMs}ms - ${maxMs}ms",
                actual = "${duration}ms",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that coroutine A completed before coroutine B.
     */
    fun validateCompletedBefore(labelA: String, labelB: String): OrderResult {
        val endA = getTerminalEvent(labelA)
            ?: return OrderResult.Failure(
                message = "Coroutine '$labelA' did not reach terminal state",
                context = mapOf("label" to labelA)
            )

        val endB = getTerminalEvent(labelB)
            ?: return OrderResult.Failure(
                message = "Coroutine '$labelB' did not reach terminal state",
                context = mapOf("label" to labelB)
            )

        return if (endA.tsNanos < endB.tsNanos) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "'$labelA' did not complete before '$labelB'",
                expected = "$labelA ends before $labelB",
                actual = "$labelA ended at ${endA.tsNanos}, $labelB ended at ${endB.tsNanos}",
                context = mapOf(
                    "labelA" to labelA,
                    "labelB" to labelB,
                    "deltaMs" to (endB.tsNanos - endA.tsNanos) / 1_000_000
                )
            )
        }
    }

    /**
     * Validates that coroutine A started before coroutine B started.
     */
    fun validateStartedBefore(labelA: String, labelB: String): OrderResult {
        val startA = recorder.find(EventSelector.labeled(labelA, "CoroutineStarted"))
            ?: return OrderResult.Failure(
                message = "Coroutine '$labelA' never started",
                context = mapOf("label" to labelA)
            )

        val startB = recorder.find(EventSelector.labeled(labelB, "CoroutineStarted"))
            ?: return OrderResult.Failure(
                message = "Coroutine '$labelB' never started",
                context = mapOf("label" to labelB)
            )

        return if (startA.tsNanos < startB.tsNanos) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "'$labelA' did not start before '$labelB'",
                expected = "$labelA starts before $labelB",
                actual = "$labelA started at ${startA.tsNanos}, $labelB started at ${startB.tsNanos}",
                context = mapOf("labelA" to labelA, "labelB" to labelB)
            )
        }
    }

    /**
     * Validates that multiple coroutines started approximately at the same time (concurrent launch).
     */
    fun validateConcurrentStart(
        labels: List<String>,
        toleranceMs: Long = 50
    ): OrderResult {
        val startTimes = labels.map { label ->
            val start = recorder.find(EventSelector.labeled(label, "CoroutineStarted"))
                ?: return OrderResult.Failure(
                    message = "Coroutine '$label' never started",
                    context = mapOf("label" to label)
                )
            label to start.tsNanos
        }

        val minTime = startTimes.minOf { it.second }
        val maxTime = startTimes.maxOf { it.second }
        val spreadMs = (maxTime - minTime) / 1_000_000

        return if (spreadMs <= toleranceMs) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutines did not start concurrently",
                expected = "Start time spread <= ${toleranceMs}ms",
                actual = "Start time spread = ${spreadMs}ms",
                context = mapOf(
                    "labels" to labels,
                    "startTimes" to startTimes.map { "${it.first}: ${it.second}" }
                )
            )
        }
    }

    /**
     * Validates that coroutine completed within a time limit from start.
     */
    fun validateMaxDuration(label: String, maxMs: Long): OrderResult {
        val duration = getDurationMs(label)
            ?: return OrderResult.Failure(
                message = "Could not calculate duration for '$label'",
                context = mapOf("label" to label)
            )

        return if (duration <= maxMs) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine exceeded max duration",
                expected = "<= ${maxMs}ms",
                actual = "${duration}ms",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that coroutine took at least a minimum duration.
     */
    fun validateMinDuration(label: String, minMs: Long): OrderResult {
        val duration = getDurationMs(label)
            ?: return OrderResult.Failure(
                message = "Could not calculate duration for '$label'",
                context = mapOf("label" to label)
            )

        return if (duration >= minMs) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine completed too quickly",
                expected = ">= ${minMs}ms",
                actual = "${duration}ms",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Gets timing statistics for a coroutine.
     */
    fun getTimingStats(label: String): TimingStats? {
        val events = recorder.forLabel(label)
        if (events.isEmpty()) return null

        val created = events.find { it.kind == "CoroutineCreated" }
        val started = events.find { it.kind == "CoroutineStarted" }
        val terminal = events.find { it.kind in listOf("CoroutineCompleted", "CoroutineFailed", "CoroutineCancelled") }

        return TimingStats(
            label = label,
            createdAt = created?.tsNanos,
            startedAt = started?.tsNanos,
            terminatedAt = terminal?.tsNanos,
            terminalState = terminal?.kind,
            totalDurationMs = if (created != null && terminal != null) {
                (terminal.tsNanos - created.tsNanos) / 1_000_000
            } else null,
            startupDelayMs = if (created != null && started != null) {
                (started.tsNanos - created.tsNanos) / 1_000_000
            } else null,
            executionTimeMs = if (started != null && terminal != null) {
                (terminal.tsNanos - started.tsNanos) / 1_000_000
            } else null
        )
    }

    /**
     * Gets duration in milliseconds from Created to terminal state.
     */
    fun getDurationMs(label: String): Long? {
        val created = recorder.find(EventSelector.labeled(label, "CoroutineCreated"))
            ?: return null
        val terminal = getTerminalEvent(label) ?: return null
        return (terminal.tsNanos - created.tsNanos) / 1_000_000
    }

    /**
     * Gets execution time (from Started to terminal) in milliseconds.
     */
    fun getExecutionTimeMs(label: String): Long? {
        val started = recorder.find(EventSelector.labeled(label, "CoroutineStarted"))
            ?: return null
        val terminal = getTerminalEvent(label) ?: return null
        return (terminal.tsNanos - started.tsNanos) / 1_000_000
    }

    /**
     * Compares durations of multiple coroutines.
     */
    fun compareDurations(vararg labels: String): List<Pair<String, Long?>> {
        return labels.map { label ->
            label to getDurationMs(label)
        }
    }

    /**
     * Gets the fastest coroutine from a list.
     */
    fun getFastest(vararg labels: String): String? {
        return labels
            .mapNotNull { label -> getDurationMs(label)?.let { label to it } }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * Gets the slowest coroutine from a list.
     */
    fun getSlowest(vararg labels: String): String? {
        return labels
            .mapNotNull { label -> getDurationMs(label)?.let { label to it } }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun getTerminalEvent(label: String): VizEvent? {
        return recorder.findAnyOf(
            EventSelector.labeled(label, "CoroutineCompleted"),
            EventSelector.labeled(label, "CoroutineFailed"),
            EventSelector.labeled(label, "CoroutineCancelled")
        )
    }

    /**
     * Timing statistics for a coroutine.
     */
    data class TimingStats(
        val label: String,
        val createdAt: Long?,
        val startedAt: Long?,
        val terminatedAt: Long?,
        val terminalState: String?,
        val totalDurationMs: Long?,
        val startupDelayMs: Long?,
        val executionTimeMs: Long?
    )
}

