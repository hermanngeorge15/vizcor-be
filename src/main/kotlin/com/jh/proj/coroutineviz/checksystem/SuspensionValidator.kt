package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineSuspended
import com.jh.proj.coroutineviz.events.VizEvent

/**
 * Validates suspension points and reasons.
 *
 * Tracks:
 * - CoroutineSuspended events
 * - Suspension reasons (delay, await, channel, etc.)
 * - Suspension durations
 *
 * Usage:
 * ```
 * val validator = SuspensionValidator(recorder)
 * validator.validateWasSuspended("worker").assertSuccess()
 * validator.validateSuspensionReason("worker", "delay").assertSuccess()
 * validator.validateSuspensionCount("worker", 3).assertSuccess()
 * ```
 */
class SuspensionValidator(private val recorder: EventRecorder) {

    /**
     * Validates that a coroutine was suspended at least once.
     */
    fun validateWasSuspended(label: String): OrderResult {
        val suspensions = getSuspensionEvents(label)

        return if (suspensions.isNotEmpty()) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine was never suspended",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that a coroutine was NOT suspended (ran synchronously).
     */
    fun validateNotSuspended(label: String): OrderResult {
        val suspensions = getSuspensionEvents(label)

        return if (suspensions.isEmpty()) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine was suspended (expected synchronous execution)",
                context = mapOf(
                    "label" to label,
                    "suspensionCount" to suspensions.size
                )
            )
        }
    }

    /**
     * Validates the exact number of suspensions.
     */
    fun validateSuspensionCount(label: String, expectedCount: Int): OrderResult {
        val suspensions = getSuspensionEvents(label)

        return if (suspensions.size == expectedCount) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Unexpected suspension count for '$label'",
                expected = expectedCount,
                actual = suspensions.size,
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that at least one suspension was for a specific reason.
     */
    fun validateSuspensionReason(label: String, expectedReason: String): OrderResult {
        val suspensions = getSuspensionEvents(label)

        if (suspensions.isEmpty()) {
            return OrderResult.Failure(
                message = "No CoroutineSuspended events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val found = suspensions.any {
            it.reason.contains(expectedReason, ignoreCase = true)
        }

        return if (found) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Expected suspension reason not found",
                expected = expectedReason,
                actual = suspensions.map { it.reason },
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that a coroutine was suspended for delay.
     */
    fun validateDelayedSuspension(label: String): OrderResult {
        return validateSuspensionReason(label, "delay")
    }

    /**
     * Validates that a coroutine was suspended for await.
     */
    fun validateAwaitSuspension(label: String): OrderResult {
        return validateSuspensionReason(label, "await")
    }

    /**
     * Validates that a coroutine was suspended for channel operation.
     */
    fun validateChannelSuspension(label: String): OrderResult {
        val suspensions = getSuspensionEvents(label)

        val channelReasons = listOf("send", "receive", "channel")
        val found = suspensions.any { suspension ->
            channelReasons.any { reason ->
                suspension.reason.contains(reason, ignoreCase = true)
            }
        }

        return if (found) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "No channel-related suspension found",
                context = mapOf(
                    "label" to label,
                    "reasons" to suspensions.map { it.reason }
                )
            )
        }
    }

    /**
     * Validates the sequence of suspension reasons.
     */
    fun validateSuspensionSequence(
        label: String,
        expectedReasons: List<String>
    ): OrderResult {
        val suspensions = getSuspensionEvents(label)

        if (suspensions.size < expectedReasons.size) {
            return OrderResult.Failure(
                message = "Not enough suspensions for sequence validation",
                expected = "${expectedReasons.size} suspensions",
                actual = "${suspensions.size} suspensions",
                context = mapOf("label" to label)
            )
        }

        val actualReasons = suspensions.map { it.reason }

        // Check if expected reasons appear in order (as subsequence)
        var expectedIndex = 0
        for (reason in actualReasons) {
            if (expectedIndex < expectedReasons.size &&
                reason.contains(expectedReasons[expectedIndex], ignoreCase = true)) {
                expectedIndex++
            }
        }

        return if (expectedIndex == expectedReasons.size) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Suspension sequence mismatch",
                expected = expectedReasons,
                actual = actualReasons,
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Gets all CoroutineSuspended events for a label.
     */
    fun getSuspensionEvents(label: String): List<CoroutineSuspended> {
        return recorder.forLabel(label)
            .filterIsInstance<CoroutineSuspended>()
            .sortedBy { it.seq }
    }

    /**
     * Gets unique suspension reasons for a coroutine.
     */
    fun getUniqueSuspensionReasons(label: String): Set<String> {
        return getSuspensionEvents(label).map { it.reason }.toSet()
    }

    /**
     * Counts suspensions by reason.
     */
    fun countSuspensionsByReason(label: String): Map<String, Int> {
        return getSuspensionEvents(label)
            .groupingBy { it.reason }
            .eachCount()
    }

    /**
     * Validates minimum suspension duration (in milliseconds).
     */
    fun validateMinSuspensionDuration(
        label: String,
        minDurationMs: Long
    ): OrderResult {
        val suspensions = getSuspensionEvents(label)

        if (suspensions.isEmpty()) {
            return OrderResult.Failure(
                message = "No CoroutineSuspended events to measure duration",
                context = mapOf("label" to label)
            )
        }

        // Check if any suspension has duration >= minDuration
        val longEnough = suspensions.any { suspension ->
            suspension.durationMillis?.let { it >= minDurationMs } ?: false
        }

        return if (longEnough) {
            OrderResult.Success
        } else {
            val durations = suspensions.mapNotNull { it.durationMillis }
            OrderResult.Failure(
                message = "No suspension met minimum duration",
                expected = ">= ${minDurationMs}ms",
                actual = "Durations: ${durations.map { "${it}ms" }}",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Calculates total time spent suspended for a coroutine (in milliseconds).
     */
    fun totalSuspensionTimeMs(label: String): Long {
        return getSuspensionEvents(label)
            .mapNotNull { it.durationMillis }
            .sum()
    }

    /**
     * Validates that suspension happened at expected function.
     */
    fun validateSuspensionFunction(label: String, expectedFunction: String): OrderResult {
        val suspensions = getSuspensionEvents(label)

        if (suspensions.isEmpty()) {
            return OrderResult.Failure(
                message = "No CoroutineSuspended events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val found = suspensions.any { suspension ->
            suspension.suspensionPoint?.function?.contains(expectedFunction, ignoreCase = true) ?: false
        }

        return if (found) {
            OrderResult.Success
        } else {
            val functions = suspensions.mapNotNull { it.suspensionPoint?.function }
            OrderResult.Failure(
                message = "Expected suspension function not found",
                expected = expectedFunction,
                actual = functions,
                context = mapOf("label" to label)
            )
        }
    }
}
