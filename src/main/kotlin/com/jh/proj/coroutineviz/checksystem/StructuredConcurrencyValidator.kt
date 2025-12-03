package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.session.VizSession

/**
 * Validates structured concurrency rules.
 *
 * Key rules:
 * 1. Parents wait for all children to complete
 * 2. BodyCompleted happens before Completed
 * 3. WaitingForChildren event appears between Body and Completed
 * 4. Exception propagation: child fails -> parent cancelled -> siblings cancelled
 * 5. Cancellation cascades: parent cancelled -> all children cancelled
 *
 * Usage:
 * ```
 * val validator = StructuredConcurrencyValidator(session, recorder)
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
 * ```
 */
class StructuredConcurrencyValidator(
    private val session: VizSession,
    private val recorder: EventRecorder
) {

    /**
     * Validates that parent waits for all children to complete.
     *
     * Rule: All children must reach terminal state BEFORE parent completes.
     */
    fun validateParentWaitsForChildren(
        parentLabel: String,
        childLabels: List<String>
    ): OrderResult {
        val parentCompleted = recorder.find(
            EventSelector.labeled(parentLabel, "CoroutineCompleted")
        ) ?: return OrderResult.Failure(
            message = "Parent never completed",
            context = mapOf("parent" to parentLabel)
        )

        for (childLabel in childLabels) {
            // Find terminal event for child
            val childTerminal = recorder.findAnyOf(
                EventSelector.labeled(childLabel, "CoroutineCompleted"),
                EventSelector.labeled(childLabel, "CoroutineFailed"),
                EventSelector.labeled(childLabel, "CoroutineCancelled")
            ) ?: return OrderResult.Failure(
                message = "Child never terminated",
                context = mapOf(
                    "parent" to parentLabel,
                    "child" to childLabel
                )
            )

            // Check: child terminates before parent completes
            if (childTerminal.seq >= parentCompleted.seq) {
                return OrderResult.Failure(
                    message = "STRUCTURED CONCURRENCY VIOLATION: Child terminated AFTER parent completed",
                    expected = "child.seq < parent.seq",
                    actual = "child.seq=${childTerminal.seq}, parent.seq=${parentCompleted.seq}",
                    context = mapOf(
                        "parent" to parentLabel,
                        "child" to childLabel,
                        "childEvent" to childTerminal.kind,
                        "violation" to "Parent completed before waiting for child"
                    )
                )
            }
        }

        return OrderResult.Success
    }

    /**
     * Validates exception propagation in structured concurrency.
     *
     * Expected sequence:
     * 1. failedChild reaches FAILED state
     * 2. parent reaches CANCELLED state (child failure propagates up)
     * 3. All siblings reach CANCELLED state (parent cancellation propagates down)
     */
    fun validateExceptionPropagation(
        failedChildLabel: String,
        parentLabel: String,
        siblingLabels: List<String>
    ): OrderResult {
        // 1. Find child failure
        val childFailed = recorder.find(
            EventSelector.labeled(failedChildLabel, "CoroutineFailed")
        ) ?: return OrderResult.Failure(
            message = "Child never failed",
            context = mapOf("expectedFailedChild" to failedChildLabel)
        )

        // 2. Find parent cancellation
        val parentCancelled = recorder.find(
            EventSelector.labeled(parentLabel, "CoroutineCancelled")
        ) ?: return OrderResult.Failure(
            message = "Parent was not cancelled (should be cancelled due to child failure)",
            context = mapOf(
                "parent" to parentLabel,
                "failedChild" to failedChildLabel,
                "childFailedAt" to childFailed.seq
            )
        )

        // Check: child failed < parent cancelled
        if (childFailed.seq >= parentCancelled.seq) {
            return OrderResult.Failure(
                message = "EXCEPTION PROPAGATION ERROR: Parent cancelled before child failed",
                expected = "child-failed.seq < parent-cancelled.seq",
                actual = "child.seq=${childFailed.seq}, parent.seq=${parentCancelled.seq}",
                context = mapOf(
                    "failedChild" to failedChildLabel,
                    "parent" to parentLabel
                )
            )
        }

        // 3. Check all siblings cancelled after parent
        for (siblingLabel in siblingLabels) {
            val siblingCancelled = recorder.find(
                EventSelector.labeled(siblingLabel, "CoroutineCancelled")
            ) ?: return OrderResult.Failure(
                message = "Sibling was not cancelled (should be cancelled when parent cancelled)",
                context = mapOf(
                    "sibling" to siblingLabel,
                    "parent" to parentLabel,
                    "parentCancelledAt" to parentCancelled.seq
                )
            )

            // Check: parent cancelled < sibling cancelled
            if (siblingCancelled.seq <= parentCancelled.seq) {
                return OrderResult.Failure(
                    message = "CANCELLATION CASCADE ERROR: Sibling cancelled before or simultaneously with parent",
                    expected = "parent-cancelled.seq < sibling-cancelled.seq",
                    actual = "parent.seq=${parentCancelled.seq}, sibling.seq=${siblingCancelled.seq}",
                    context = mapOf(
                        "parent" to parentLabel,
                        "sibling" to siblingLabel
                    )
                )
            }
        }

        return OrderResult.Success
    }

    /**
     * Validates WaitingForChildren events are emitted correctly.
     *
     * Expected order:
     * CoroutineBodyCompleted -> WaitingForChildren -> CoroutineCompleted
     */
    fun validateWaitingForChildren(parentLabel: String): OrderResult {
        val events = recorder.forLabel(parentLabel)

        val bodyCompleted = events.find { it.kind == "CoroutineBodyCompleted" }
        val waitingFor = events.find { it.kind == "WaitingForChildren" }
        val completed = events.find { it.kind == "CoroutineCompleted" }

        if (bodyCompleted == null) {
            return OrderResult.Failure(
                message = "No CoroutineBodyCompleted event found",
                context = mapOf("parent" to parentLabel)
            )
        }

        if (completed == null) {
            return OrderResult.Failure(
                message = "No CoroutineCompleted event found",
                context = mapOf("parent" to parentLabel)
            )
        }

        // Check order: BodyCompleted < Completed
        if (bodyCompleted.seq >= completed.seq) {
            return OrderResult.Failure(
                message = "BodyCompleted should occur before Completed",
                expected = "bodyCompleted.seq < completed.seq",
                actual = "bodyCompleted.seq=${bodyCompleted.seq}, completed.seq=${completed.seq}",
                context = mapOf("parent" to parentLabel)
            )
        }

        // If WaitingForChildren exists, check its position
        if (waitingFor != null) {
            if (waitingFor.seq <= bodyCompleted.seq || waitingFor.seq >= completed.seq) {
                return OrderResult.Failure(
                    message = "WaitingForChildren should be between BodyCompleted and Completed",
                    expected = "bodyCompleted < waitingFor < completed",
                    actual = "body=${bodyCompleted.seq}, waiting=${waitingFor.seq}, completed=${completed.seq}",
                    context = mapOf("parent" to parentLabel)
                )
            }
        }

        return OrderResult.Success
    }

    /**
     * Validates cancellation cascade: when parent is cancelled, all children must be cancelled.
     */
    fun validateCancellationCascade(
        parentLabel: String,
        childLabels: List<String>
    ): OrderResult {
        val parentCancelled = recorder.find(
            EventSelector.labeled(parentLabel, "CoroutineCancelled")
        ) ?: return OrderResult.Failure(
            message = "Parent was not cancelled",
            context = mapOf("parent" to parentLabel)
        )

        for (childLabel in childLabels) {
            val childCancelled = recorder.find(
                EventSelector.labeled(childLabel, "CoroutineCancelled")
            ) ?: return OrderResult.Failure(
                message = "Child was not cancelled (should be cancelled when parent cancelled)",
                context = mapOf(
                    "parent" to parentLabel,
                    "child" to childLabel
                )
            )

            // Child should be cancelled after parent
            if (childCancelled.seq <= parentCancelled.seq) {
                return OrderResult.Failure(
                    message = "Child cancelled before or simultaneously with parent",
                    expected = "parent-cancelled < child-cancelled",
                    actual = "parent.seq=${parentCancelled.seq}, child.seq=${childCancelled.seq}",
                    context = mapOf(
                        "parent" to parentLabel,
                        "child" to childLabel
                    )
                )
            }
        }

        return OrderResult.Success
    }

    /**
     * Validates that a parent coroutine properly completed with all its children.
     * This is a softer validation that checks completion without requiring specific child labels.
     */
    fun validateParentCompleted(parentLabel: String): OrderResult {
        val parentCompleted = recorder.find(
            EventSelector.labeled(parentLabel, "CoroutineCompleted")
        )

        val parentCancelled = recorder.find(
            EventSelector.labeled(parentLabel, "CoroutineCancelled")
        )

        val parentFailed = recorder.find(
            EventSelector.labeled(parentLabel, "CoroutineFailed")
        )

        return when {
            parentCompleted != null -> OrderResult.Success
            parentCancelled != null -> OrderResult.Failure(
                message = "Parent was cancelled instead of completed",
                context = mapOf("parent" to parentLabel)
            )
            parentFailed != null -> OrderResult.Failure(
                message = "Parent failed instead of completed",
                context = mapOf("parent" to parentLabel)
            )
            else -> OrderResult.Failure(
                message = "Parent never reached terminal state",
                context = mapOf("parent" to parentLabel)
            )
        }
    }

    /**
     * Comprehensive validation combining all structured concurrency rules.
     */
    fun validateAll(parentLabel: String): OrderResult {
        // Get hierarchy from projection service
        val hierarchy = session.projectionService.getHierarchyTree()
        val parentNode = hierarchy.find { it.name == parentLabel || it.id == parentLabel }
            ?: return OrderResult.Failure(
                message = "Parent not found in hierarchy",
                context = mapOf("parent" to parentLabel)
            )

        val childLabels = parentNode.children.mapNotNull { childId ->
            hierarchy.find { it.id == childId }?.name
        }

        if (childLabels.isEmpty()) {
            // No children - only validate WaitingForChildren
            return validateWaitingForChildren(parentLabel)
        }

        // 1. Validate parent waits for children
        val parentWaitsResult = validateParentWaitsForChildren(parentLabel, childLabels)
        if (parentWaitsResult is OrderResult.Failure) {
            return parentWaitsResult
        }

        // 2. Validate WaitingForChildren events
        val waitingResult = validateWaitingForChildren(parentLabel)
        if (waitingResult is OrderResult.Failure) {
            return waitingResult
        }

        return OrderResult.Success
    }
}

