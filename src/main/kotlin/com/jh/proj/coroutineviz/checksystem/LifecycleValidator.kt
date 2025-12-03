package com.jh.proj.coroutineviz.checksystem

/**
 * Validates coroutine lifecycle state machine transitions.
 *
 * Valid state machine:
 * ```
 * INIT -> Created -> Started -> (Suspended <-> Resumed)* -> Terminal
 *
 * Terminal states: Completed | Failed | Cancelled
 * ```
 *
 * Usage:
 * ```
 * val validator = LifecycleValidator(recorder)
 * validator.validateCoroutineLifecycle("parent").assertSuccess()
 * validator.validateCoroutineLifecycle("child-1").assertSuccess()
 * ```
 */
class LifecycleValidator(private val recorder: EventRecorder) {

    /**
     * Validates complete lifecycle for a coroutine.
     * Checks:
     * 1. Valid state transitions
     * 2. Reaches terminal state
     * 3. No invalid transitions
     */
    fun validateCoroutineLifecycle(label: String): OrderResult {
        val events = recorder.forLabel(label)

        if (events.isEmpty()) {
            return OrderResult.Failure(
                message = "No events found for coroutine",
                context = mapOf("label" to label)
            )
        }

        var state = State.INIT
        val transitions = mutableListOf<String>()

        for ((index, event) in events.withIndex()) {
            val nextState = eventToState(event.kind) ?: continue

            transitions.add("$state -> $nextState")

            if (!isValidTransition(state, nextState)) {
                return OrderResult.Failure(
                    message = "Invalid lifecycle transition for '$label'",
                    expected = "Valid transition from $state",
                    actual = "$state -> $nextState",
                    context = mapOf(
                        "label" to label,
                        "eventIndex" to index,
                        "event" to event.kind,
                        "seq" to event.seq,
                        "transitions" to transitions.joinToString(" -> ")
                    )
                )
            }

            state = nextState
        }

        // Check terminal state
        if (!state.isTerminal()) {
            return OrderResult.Failure(
                message = "Coroutine did not reach terminal state",
                expected = "COMPLETED, FAILED, or CANCELLED",
                actual = state.toString(),
                context = mapOf(
                    "label" to label,
                    "finalState" to state,
                    "transitions" to transitions.joinToString(" -> ")
                )
            )
        }

        return OrderResult.Success
    }

    /**
     * Validates lifecycle without requiring terminal state.
     * Useful for validating in-progress coroutines.
     */
    fun validatePartialLifecycle(label: String): OrderResult {
        val events = recorder.forLabel(label)

        if (events.isEmpty()) {
            return OrderResult.Failure(
                message = "No events found for coroutine",
                context = mapOf("label" to label)
            )
        }

        var state = State.INIT
        val transitions = mutableListOf<String>()

        for ((index, event) in events.withIndex()) {
            val nextState = eventToState(event.kind) ?: continue

            transitions.add("$state -> $nextState")

            if (!isValidTransition(state, nextState)) {
                return OrderResult.Failure(
                    message = "Invalid lifecycle transition for '$label'",
                    expected = "Valid transition from $state",
                    actual = "$state -> $nextState",
                    context = mapOf(
                        "label" to label,
                        "eventIndex" to index,
                        "event" to event.kind,
                        "seq" to event.seq,
                        "transitions" to transitions.joinToString(" -> ")
                    )
                )
            }

            state = nextState
        }

        return OrderResult.Success
    }

    /**
     * Validates that all coroutines with given labels have valid lifecycles.
     */
    fun validateAllLifecycles(vararg labels: String): OrderResult {
        for (label in labels) {
            val result = validateCoroutineLifecycle(label)
            if (result is OrderResult.Failure) {
                return result
            }
        }
        return OrderResult.Success
    }

    /**
     * Checks if coroutine reached a specific terminal state.
     */
    fun hasTerminalState(label: String, expectedState: State): OrderResult {
        val events = recorder.forLabel(label)
        val lastLifecycleEvent = events.lastOrNull { eventToState(it.kind) != null }

        if (lastLifecycleEvent == null) {
            return OrderResult.Failure(
                message = "No lifecycle events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val actualState = eventToState(lastLifecycleEvent.kind)!!

        return if (actualState == expectedState) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine did not reach expected terminal state",
                expected = expectedState,
                actual = actualState,
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Gets the current state of a coroutine.
     */
    fun getCurrentState(label: String): State? {
        val events = recorder.forLabel(label)
        val lastLifecycleEvent = events.lastOrNull { eventToState(it.kind) != null }
        return lastLifecycleEvent?.let { eventToState(it.kind) }
    }

    /**
     * Checks if a coroutine was suspended at least once.
     */
    fun wasSuspended(label: String): OrderResult {
        val events = recorder.forLabel(label)
        val wasSuspended = events.any { it.kind == "CoroutineSuspended" }

        return if (wasSuspended) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine was never suspended",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Counts the number of suspend/resume cycles.
     */
    fun suspendResumeCount(label: String): Int {
        val events = recorder.forLabel(label)
        return events.count { it.kind == "CoroutineSuspended" }
    }

    private fun eventToState(kind: String): State? = when (kind) {
        "CoroutineCreated" -> State.CREATED
        "CoroutineStarted" -> State.STARTED
        "CoroutineSuspended" -> State.SUSPENDED
        "CoroutineResumed" -> State.RESUMED
        "CoroutineCompleted" -> State.COMPLETED
        "CoroutineFailed" -> State.FAILED
        "CoroutineCancelled" -> State.CANCELLED
        else -> null
    }

    private fun isValidTransition(from: State, to: State): Boolean = when (from) {
        State.INIT -> to == State.CREATED
        State.CREATED -> to == State.STARTED
        State.STARTED -> to in setOf(
            State.SUSPENDED,
            State.COMPLETED,
            State.FAILED,
            State.CANCELLED
        )
        State.SUSPENDED -> to in setOf(
            State.RESUMED,
            State.CANCELLED,
            State.FAILED
        )
        State.RESUMED -> to in setOf(
            State.SUSPENDED,
            State.COMPLETED,
            State.FAILED,
            State.CANCELLED
        )
        State.COMPLETED, State.FAILED, State.CANCELLED -> false  // Terminal
    }

    /**
     * Coroutine lifecycle states.
     */
    enum class State {
        INIT,
        CREATED,
        STARTED,
        SUSPENDED,
        RESUMED,
        COMPLETED,
        FAILED,
        CANCELLED;

        fun isTerminal() = this in setOf(COMPLETED, FAILED, CANCELLED)
        fun isActive() = this in setOf(STARTED, SUSPENDED, RESUMED)
    }
}

