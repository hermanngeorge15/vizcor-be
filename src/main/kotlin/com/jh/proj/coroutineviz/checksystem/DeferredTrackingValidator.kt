package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.CoroutineEvent

/**
 * Validates async/await behavior and deferred tracking.
 *
 * Validates:
 * 1. DeferredCreated events
 * 2. DeferredAwaitStarted/Completed pairs
 * 3. DeferredValueAvailable timing
 * 4. Multiple awaiters on same deferred
 *
 * Usage:
 * ```
 * val validator = DeferredTrackingValidator(recorder)
 * validator.validateAsyncAwaitPair("compute", "parent").assertSuccess()
 * validator.validateMultipleAwaiters("shared-deferred", listOf("awaiter-1", "awaiter-2"))
 *     .assertSuccess()
 * ```
 */
class DeferredTrackingValidator(private val recorder: EventRecorder) {

    /**
     * Validates basic async coroutine lifecycle.
     *
     * Checks:
     * 1. Async coroutine created
     * 2. Async coroutine started
     * 3. Async coroutine completed
     */
    fun validateAsyncLifecycle(asyncLabel: String): OrderResult {
        // Find async coroutine creation
        val asyncCreated = recorder.find(
            EventSelector.labeled(asyncLabel, "CoroutineCreated")
        ) ?: return OrderResult.Failure(
            message = "Async coroutine never created",
            context = mapOf("asyncLabel" to asyncLabel)
        )

        // Find async completion
        val asyncCompleted = recorder.find(
            EventSelector.labeled(asyncLabel, "CoroutineCompleted")
        ) ?: return OrderResult.Failure(
            message = "Async coroutine never completed",
            context = mapOf("asyncLabel" to asyncLabel)
        )

        // Check ordering
        if (asyncCreated.seq >= asyncCompleted.seq) {
            return OrderResult.Failure(
                message = "Async created after completed (invalid)",
                expected = "created < completed",
                actual = "created.seq=${asyncCreated.seq}, completed.seq=${asyncCompleted.seq}"
            )
        }

        return OrderResult.Success
    }

    /**
     * Validates async/await pair for a deferred.
     *
     * Checks:
     * 1. Async coroutine created
     * 2. Await started event exists
     * 3. Await completed event exists
     * 4. Proper ordering
     */
    fun validateAsyncAwaitPair(
        asyncLabel: String,
        awaiterLabel: String
    ): OrderResult {
        // Find async coroutine creation
        val asyncCreated = recorder.find(
            EventSelector.labeled(asyncLabel, "CoroutineCreated")
        ) ?: return OrderResult.Failure(
            message = "Async coroutine never created",
            context = mapOf("asyncLabel" to asyncLabel)
        )

        // Find await started
        val awaitStarted = recorder.find(
            EventSelector.labeled(awaiterLabel, "DeferredAwaitStarted")
        )

        // Find await completed
        val awaitCompleted = recorder.find(
            EventSelector.labeled(awaiterLabel, "DeferredAwaitCompleted")
        )

        // Find value available (optional)
        val valueAvailable = recorder.find(
            EventSelector.labeled(asyncLabel, "DeferredValueAvailable")
        )

        // Validate await started before await completed
        if (awaitStarted != null && awaitCompleted != null) {
            if (awaitStarted.seq >= awaitCompleted.seq) {
                return OrderResult.Failure(
                    message = "Await started after await completed",
                    expected = "awaitStarted < awaitCompleted",
                    actual = "awaitStarted.seq=${awaitStarted.seq}, awaitCompleted.seq=${awaitCompleted.seq}"
                )
            }
        }

        // If value available exists, it should be before await completed
        if (valueAvailable != null && awaitCompleted != null) {
            if (valueAvailable.seq >= awaitCompleted.seq) {
                return OrderResult.Failure(
                    message = "Value became available after await completed",
                    expected = "valueAvailable < awaitCompleted",
                    actual = "valueAvailable.seq=${valueAvailable.seq}, awaitCompleted.seq=${awaitCompleted.seq}"
                )
            }
        }

        return OrderResult.Success
    }

    /**
     * Validates multiple coroutines awaiting the same deferred.
     *
     * Checks:
     * 1. All awaiters eventually complete
     * 2. Async coroutine completes
     */
    fun validateMultipleAwaiters(
        asyncLabel: String,
        awaiterLabels: List<String>
    ): OrderResult {
        // Find async completion
        val asyncCompleted = recorder.find(
            EventSelector.labeled(asyncLabel, "CoroutineCompleted")
        ) ?: return OrderResult.Failure(
            message = "Async coroutine never completed",
            context = mapOf("asyncLabel" to asyncLabel)
        )

        // Check each awaiter completed after async
        for (awaiterLabel in awaiterLabels) {
            recorder.findAnyOf(
                EventSelector.labeled(awaiterLabel, "CoroutineCompleted"),
                EventSelector.labeled(awaiterLabel, "CoroutineFailed"),
                EventSelector.labeled(awaiterLabel, "CoroutineCancelled")
            ) ?: return OrderResult.Failure(
                message = "Awaiter never terminated",
                context = mapOf(
                    "awaiter" to awaiterLabel,
                    "async" to asyncLabel
                )
            )
        }

        return OrderResult.Success
    }

    /**
     * Validates that async task completes before awaiter completes its await.
     */
    fun validateAsyncCompletesBeforeAwait(
        asyncLabel: String,
        awaiterLabel: String
    ): OrderResult {
        val asyncCompleted = recorder.find(
            EventSelector.labeled(asyncLabel, "CoroutineCompleted")
        ) ?: return OrderResult.Failure(
            message = "Async coroutine never completed",
            context = mapOf("asyncLabel" to asyncLabel)
        )

        val awaitCompleted = recorder.find(
            EventSelector.labeled(awaiterLabel, "DeferredAwaitCompleted")
        ) ?: return OrderResult.Failure(
            message = "Await never completed",
            context = mapOf("awaiterLabel" to awaiterLabel)
        )

        if (asyncCompleted.seq > awaitCompleted.seq) {
            return OrderResult.Failure(
                message = "Async completed after await (should complete before)",
                expected = "asyncCompleted < awaitCompleted",
                actual = "async.seq=${asyncCompleted.seq}, await.seq=${awaitCompleted.seq}"
            )
        }

        return OrderResult.Success
    }

    /**
     * Validates that async failure propagates to awaiter.
     */
    fun validateAsyncFailurePropagation(
        asyncLabel: String,
        awaiterLabel: String
    ): OrderResult {
        val asyncFailed = recorder.find(
            EventSelector.labeled(asyncLabel, "CoroutineFailed")
        ) ?: return OrderResult.Failure(
            message = "Async coroutine did not fail (expected failure for this test)",
            context = mapOf("asyncLabel" to asyncLabel)
        )

        // Awaiter should either fail or be cancelled
        val awaiterTerminal = recorder.findAnyOf(
            EventSelector.labeled(awaiterLabel, "CoroutineFailed"),
            EventSelector.labeled(awaiterLabel, "CoroutineCancelled")
        ) ?: return OrderResult.Failure(
            message = "Awaiter did not fail or get cancelled after async failed",
            context = mapOf(
                "async" to asyncLabel,
                "awaiter" to awaiterLabel
            )
        )

        // Awaiter should terminate after async failed
        if (awaiterTerminal.seq <= asyncFailed.seq) {
            return OrderResult.Failure(
                message = "Awaiter terminated before async failed",
                expected = "asyncFailed < awaiterTerminal",
                actual = "async.seq=${asyncFailed.seq}, awaiter.seq=${awaiterTerminal.seq}"
            )
        }

        return OrderResult.Success
    }

    /**
     * Counts how many coroutines awaited based on DeferredAwaitStarted events.
     */
    fun countAwaiters(): Int {
        return recorder.all()
            .filter { it.kind == "DeferredAwaitStarted" }
            .count()
    }

    /**
     * Validates that all awaiters for a deferred received the value.
     */
    fun validateAllAwaitersReceived(awaiterLabels: List<String>): OrderResult {
        for (awaiterLabel in awaiterLabels) {
            val awaitCompleted = recorder.find(
                EventSelector.labeled(awaiterLabel, "DeferredAwaitCompleted")
            ) ?: return OrderResult.Failure(
                message = "Awaiter did not complete await",
                context = mapOf("awaiter" to awaiterLabel)
            )
        }
        return OrderResult.Success
    }
}

