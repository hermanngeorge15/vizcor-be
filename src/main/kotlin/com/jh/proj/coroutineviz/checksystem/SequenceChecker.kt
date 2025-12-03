package com.jh.proj.coroutineviz.checksystem

/**
 * Verifies events appear in strict sequential order.
 *
 * Use cases:
 * - Verify exact event sequence: Created -> Started -> Completed
 * - Verify subsequence appears in order (with gaps allowed)
 * - Verify lifecycle patterns
 *
 * Usage:
 * e Completed
 * 3. WaitingForChildren event appears between Body and Completed
 * 4. Exception propagation: child fails -> parent cancelled -> siblings cancelled
 * 5. Cancellation cascades: parent cancelled -> all children cancelled
 *
 * Usage:
 *  * val validator = StructuredConcurrencyValidator(session, recorder)
 *
 * // Validate parent waits for children
 * validator.validateParentWaitsForChildren("parent", listOf("child-1", "child-2"))
 *     .assertSuccess()
 *
 * // Validate exception propagation
 * validator.validateExceptionPropagation(
 *     failedChildLabel = "child-2",
 *     parentLabel = "parent",
 *     siblingLabels = listOf("child-1")
 * ).assertSuccess()
 * val checker = SequenceChecker(recorder)
 * checker.checkSequence("parent", listOf(
 * "CoroutineCreated",
 * "CoroutineStarted",
 * "CoroutineCompleted"
 * )).assertSuccess()
*/
class SequenceChecker(private val recorder: EventRecorder) {

    /**
     * Verifies events appear in EXACT order with no gaps.
     * All events must match exactly in sequence.
     */
    fun checkSequence(label: String, expectedKinds: List<String>): OrderResult {
        val events = recorder.forLabel(label)
        val actualKinds = events.map { it.kind }

        return if (actualKinds == expectedKinds) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Event sequence mismatch for '$label'",
                expected = expectedKinds,
                actual = actualKinds,
                context = mapOf(
                    "label" to label,
                    "eventCount" to events.size,
                    "missing" to (expectedKinds - actualKinds.toSet()),
                    "extra" to (actualKinds.toSet() - expectedKinds.toSet())
                )
            )
        }
    }

    /**
     * Verifies events contain expected kinds in order.
     * Allows additional events in between (subsequence matching).
     */
    fun checkContainsSequence(label: String, expectedKinds: List<String>): OrderResult {
        val events = recorder.forLabel(label)
        val actualKinds = events.map { it.kind }

        var searchIndex = 0
        val foundIndices = mutableListOf<Int>()

        for (expectedKind in expectedKinds) {
            // Find element starting from searchIndex
            val foundIndex = actualKinds.subList(searchIndex, actualKinds.size).indexOf(expectedKind)
            if (foundIndex == -1) {
                return OrderResult.Failure(
                    message = "Expected event '$expectedKind' not found in sequence for '$label'",
                    expected = expectedKinds,
                    actual = actualKinds,
                    context = mapOf(
                        "label" to label,
                        "searchStartedAt" to searchIndex,
                        "foundSoFar" to foundIndices,
                        "missingEvent" to expectedKind
                    )
                )
            }
            val absoluteIndex = searchIndex + foundIndex
            foundIndices.add(absoluteIndex)
            searchIndex = absoluteIndex + 1
        }

        return OrderResult.Success
    }

    /**
     * Verifies a standard coroutine lifecycle pattern.
     */
    fun checkLifecycle(label: String, lifecycle: Lifecycle): OrderResult {
        return when (lifecycle) {
            Lifecycle.COMPLETE_SUCCESS -> checkContainsSequence(
                label,
                listOf("CoroutineCreated", "CoroutineStarted", "CoroutineCompleted")
            )
            Lifecycle.CANCELLED -> checkContainsSequence(
                label,
                listOf("CoroutineCreated", "CoroutineStarted", "CoroutineCancelled")
            )
            Lifecycle.FAILED -> checkContainsSequence(
                label,
                listOf("CoroutineCreated", "CoroutineStarted", "CoroutineFailed")
            )
            Lifecycle.STARTED_ONLY -> checkContainsSequence(
                label,
                listOf("CoroutineCreated", "CoroutineStarted")
            )
        }
    }

    /**
     * Verifies that specific event appears before another for same coroutine.
     */
    fun checkEventBefore(label: String, before: String, after: String): OrderResult {
        val events = recorder.forLabel(label)
        val beforeIndex = events.indexOfFirst { it.kind == before }
        val afterIndex = events.indexOfFirst { it.kind == after }

        return when {
            beforeIndex == -1 -> OrderResult.Failure(
                message = "Event '$before' not found for '$label'",
                context = mapOf("label" to label, "event" to before)
            )
            afterIndex == -1 -> OrderResult.Failure(
                message = "Event '$after' not found for '$label'",
                context = mapOf("label" to label, "event" to after)
            )
            beforeIndex < afterIndex -> OrderResult.Success
            else -> OrderResult.Failure(
                message = "Event order violation for '$label'",
                expected = "$before before $after",
                actual = "$before at index $beforeIndex, $after at index $afterIndex",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Standard coroutine lifecycle patterns.
     */
    enum class Lifecycle {
        /** Created -> Started -> Completed */
        COMPLETE_SUCCESS,

        /** Created -> Started -> Cancelled */
        CANCELLED,

        /** Created -> Started -> Failed */
        FAILED,

        /** Created -> Started (incomplete) */
        STARTED_ONLY
    }
}