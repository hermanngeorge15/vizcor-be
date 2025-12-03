package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.JobStateChanged

/**
 * Validates Job state changes and transitions.
 *
 * Tracks JobStateChanged events to verify:
 * - Active/Completed/Cancelled state transitions
 * - Children count changes
 * - Job lifecycle correctness
 *
 * Usage:
 * ```
 * val validator = JobStateValidator(recorder)
 * validator.validateJobWasActive("parent").assertSuccess()
 * validator.validateJobCompleted("parent").assertSuccess()
 * validator.validateChildrenCount("parent", 2).assertSuccess()
 * ```
 */
class JobStateValidator(private val recorder: EventRecorder) {

    /**
     * Validates that a job was active at some point.
     */
    fun validateJobWasActive(label: String): OrderResult {
        val jobStates = getJobStateEvents(label)

        if (jobStates.isEmpty()) {
            return OrderResult.Failure(
                message = "No JobStateChanged events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val wasActive = jobStates.any { it.isActive }

        return if (wasActive) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Job was never active",
                context = mapOf(
                    "label" to label,
                    "states" to jobStates.map { "active=${it.isActive}, completed=${it.isCompleted}, cancelled=${it.isCancelled}" }
                )
            )
        }
    }

    /**
     * Validates that a job completed successfully.
     */
    fun validateJobCompleted(label: String): OrderResult {
        val jobStates = getJobStateEvents(label)

        if (jobStates.isEmpty()) {
            return OrderResult.Failure(
                message = "No JobStateChanged events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val lastState = jobStates.last()

        return if (lastState.isCompleted && !lastState.isCancelled) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Job did not complete successfully",
                expected = "isCompleted=true, isCancelled=false",
                actual = "isCompleted=${lastState.isCompleted}, isCancelled=${lastState.isCancelled}",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that a job was cancelled.
     */
    fun validateJobCancelled(label: String): OrderResult {
        val jobStates = getJobStateEvents(label)

        if (jobStates.isEmpty()) {
            return OrderResult.Failure(
                message = "No JobStateChanged events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val lastState = jobStates.last()

        return if (lastState.isCancelled) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Job was not cancelled",
                expected = "isCancelled=true",
                actual = "isCancelled=${lastState.isCancelled}",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates the maximum children count observed for a job.
     */
    fun validateMaxChildrenCount(label: String, expectedMax: Int): OrderResult {
        val jobStates = getJobStateEvents(label)

        if (jobStates.isEmpty()) {
            return OrderResult.Failure(
                message = "No JobStateChanged events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val maxChildren = jobStates.maxOf { it.childrenCount }

        return if (maxChildren == expectedMax) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Unexpected max children count for '$label'",
                expected = expectedMax,
                actual = maxChildren,
                context = mapOf(
                    "label" to label,
                    "childrenHistory" to jobStates.map { it.childrenCount }
                )
            )
        }
    }

    /**
     * Validates that children count eventually reaches zero (all children completed).
     */
    fun validateAllChildrenCompleted(label: String): OrderResult {
        val jobStates = getJobStateEvents(label)

        if (jobStates.isEmpty()) {
            return OrderResult.Failure(
                message = "No JobStateChanged events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val lastState = jobStates.last()

        return if (lastState.childrenCount == 0) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Not all children completed",
                expected = "childrenCount=0",
                actual = "childrenCount=${lastState.childrenCount}",
                context = mapOf(
                    "label" to label,
                    "childrenHistory" to jobStates.map { it.childrenCount }
                )
            )
        }
    }

    /**
     * Validates the state transition sequence for a job.
     * Standard sequence: Active -> Completing -> Completed
     */
    fun validateStateSequence(label: String): OrderResult {
        val jobStates = getJobStateEvents(label)

        if (jobStates.isEmpty()) {
            return OrderResult.Failure(
                message = "No JobStateChanged events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        // Check starts as active
        val firstState = jobStates.first()
        if (!firstState.isActive) {
            return OrderResult.Failure(
                message = "Job did not start as active",
                expected = "First state: isActive=true",
                actual = "First state: isActive=${firstState.isActive}",
                context = mapOf("label" to label)
            )
        }

        // Check ends in terminal state
        val lastState = jobStates.last()
        if (!lastState.isCompleted && !lastState.isCancelled) {
            return OrderResult.Failure(
                message = "Job did not reach terminal state",
                expected = "Last state: isCompleted=true or isCancelled=true",
                actual = "Last state: isCompleted=${lastState.isCompleted}, isCancelled=${lastState.isCancelled}",
                context = mapOf("label" to label)
            )
        }

        return OrderResult.Success
    }

    /**
     * Gets all JobStateChanged events for a label.
     */
    fun getJobStateEvents(label: String): List<JobStateChanged> {
        return recorder.forLabel(label)
            .filterIsInstance<JobStateChanged>()
            .sortedBy { it.seq }
    }

    /**
     * Gets the final job state for a label.
     */
    fun getFinalState(label: String): JobState? {
        val lastEvent = getJobStateEvents(label).lastOrNull() ?: return null

        return when {
            lastEvent.isCancelled -> JobState.CANCELLED
            lastEvent.isCompleted -> JobState.COMPLETED
            lastEvent.isActive -> JobState.ACTIVE
            else -> JobState.NEW
        }
    }

    /**
     * Validates that job state changes follow expected pattern.
     */
    fun validateJobStatePattern(
        label: String,
        expectedPattern: List<JobState>
    ): OrderResult {
        val jobStates = getJobStateEvents(label)

        if (jobStates.isEmpty()) {
            return OrderResult.Failure(
                message = "No JobStateChanged events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val actualPattern = jobStates.map { event ->
            when {
                event.isCancelled -> JobState.CANCELLED
                event.isCompleted -> JobState.COMPLETED
                event.isActive -> JobState.ACTIVE
                else -> JobState.NEW
            }
        }

        // Check if actual contains expected as subsequence
        var expectedIndex = 0
        for (state in actualPattern) {
            if (expectedIndex < expectedPattern.size && state == expectedPattern[expectedIndex]) {
                expectedIndex++
            }
        }

        return if (expectedIndex == expectedPattern.size) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Job state pattern mismatch for '$label'",
                expected = expectedPattern,
                actual = actualPattern,
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Job states for pattern matching.
     */
    enum class JobState {
        NEW,
        ACTIVE,
        COMPLETING,
        COMPLETED,
        CANCELLING,
        CANCELLED
    }
}

