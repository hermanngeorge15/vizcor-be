package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.events.DispatcherSelected
import com.jh.proj.coroutineviz.events.ThreadAssigned

/**
 * Validates dispatcher and thread assignments.
 *
 * Tracks:
 * - DispatcherSelected events
 * - ThreadAssigned events
 * - Thread pool usage
 * - Dispatcher context switches
 *
 * Usage:
 * ```
 * val validator = DispatcherValidator(recorder)
 * validator.validateDispatcher("io-worker", "Dispatchers.IO").assertSuccess()
 * validator.validateThreadPool("io-worker", "DefaultDispatcher-worker").assertSuccess()
 * validator.validateNoMainThread("background-task").assertSuccess()
 * ```
 */
class DispatcherValidator(private val recorder: EventRecorder) {

    /**
     * Validates that a coroutine was dispatched to a specific dispatcher.
     */
    fun validateDispatcher(label: String, expectedDispatcher: String): OrderResult {
        val dispatcherEvents = getDispatcherEvents(label)

        if (dispatcherEvents.isEmpty()) {
            return OrderResult.Failure(
                message = "No DispatcherSelected events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val found = dispatcherEvents.any { it.dispatcherName.contains(expectedDispatcher, ignoreCase = true) }

        return if (found) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Expected dispatcher not found for '$label'",
                expected = expectedDispatcher,
                actual = dispatcherEvents.map { it.dispatcherName },
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that a coroutine ran on a thread from expected pool.
     */
    fun validateThreadPool(label: String, expectedThreadPrefix: String): OrderResult {
        val threadEvents = getThreadEvents(label)

        if (threadEvents.isEmpty()) {
            return OrderResult.Failure(
                message = "No ThreadAssigned events found for '$label'",
                context = mapOf("label" to label)
            )
        }

        val found = threadEvents.any { it.threadName.startsWith(expectedThreadPrefix) }

        return if (found) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Expected thread pool not used for '$label'",
                expected = "Thread starting with '$expectedThreadPrefix'",
                actual = threadEvents.map { it.threadName },
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that a coroutine did NOT run on the main thread.
     */
    fun validateNoMainThread(label: String): OrderResult {
        val threadEvents = getThreadEvents(label)

        if (threadEvents.isEmpty()) {
            // No thread events - can't validate
            return OrderResult.Success
        }

        val mainThreadNames = listOf("main", "Main", "UI", "AndroidMain")
        val ranOnMain = threadEvents.any { event ->
            mainThreadNames.any { main -> event.threadName.contains(main) }
        }

        return if (!ranOnMain) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Coroutine ran on main thread (should be background)",
                context = mapOf(
                    "label" to label,
                    "threads" to threadEvents.map { it.threadName }
                )
            )
        }
    }

    /**
     * Validates that a coroutine used multiple threads (thread switching occurred).
     */
    fun validateThreadSwitching(label: String): OrderResult {
        val threadEvents = getThreadEvents(label)

        if (threadEvents.size < 2) {
            return OrderResult.Failure(
                message = "Not enough ThreadAssigned events to detect switching",
                context = mapOf(
                    "label" to label,
                    "eventCount" to threadEvents.size
                )
            )
        }

        val uniqueThreads = threadEvents.map { it.threadName }.toSet()

        return if (uniqueThreads.size > 1) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "No thread switching detected",
                expected = "Multiple different threads",
                actual = "Only thread: ${uniqueThreads.first()}",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates that a coroutine stayed on the same thread (no switching).
     */
    fun validateNoThreadSwitching(label: String): OrderResult {
        val threadEvents = getThreadEvents(label)

        if (threadEvents.isEmpty()) {
            return OrderResult.Success
        }

        val uniqueThreads = threadEvents.map { it.threadName }.toSet()

        return if (uniqueThreads.size == 1) {
            OrderResult.Success
        } else {
            OrderResult.Failure(
                message = "Unexpected thread switching detected",
                expected = "Single thread",
                actual = "Multiple threads: $uniqueThreads",
                context = mapOf("label" to label)
            )
        }
    }

    /**
     * Validates dispatcher change (context switch).
     */
    fun validateDispatcherChange(
        label: String,
        fromDispatcher: String,
        toDispatcher: String
    ): OrderResult {
        val dispatcherEvents = getDispatcherEvents(label)

        if (dispatcherEvents.size < 2) {
            return OrderResult.Failure(
                message = "Not enough DispatcherSelected events to detect change",
                context = mapOf(
                    "label" to label,
                    "eventCount" to dispatcherEvents.size
                )
            )
        }

        // Look for the transition
        for (i in 0 until dispatcherEvents.size - 1) {
            val from = dispatcherEvents[i]
            val to = dispatcherEvents[i + 1]

            if (from.dispatcherName.contains(fromDispatcher, ignoreCase = true) &&
                to.dispatcherName.contains(toDispatcher, ignoreCase = true)) {
                return OrderResult.Success
            }
        }

        return OrderResult.Failure(
            message = "Dispatcher change not found",
            expected = "$fromDispatcher -> $toDispatcher",
            actual = dispatcherEvents.map { it.dispatcherName },
            context = mapOf("label" to label)
        )
    }

    /**
     * Gets all DispatcherSelected events for a label.
     */
    fun getDispatcherEvents(label: String): List<DispatcherSelected> {
        return recorder.forLabel(label)
            .filterIsInstance<DispatcherSelected>()
            .sortedBy { it.seq }
    }

    /**
     * Gets all ThreadAssigned events for a label.
     */
    fun getThreadEvents(label: String): List<ThreadAssigned> {
        return recorder.forLabel(label)
            .filterIsInstance<ThreadAssigned>()
            .sortedBy { it.seq }
    }

    /**
     * Gets unique dispatchers used by a coroutine.
     */
    fun getUniqueDispatchers(label: String): Set<String> {
        return getDispatcherEvents(label).map { it.dispatcherName }.toSet()
    }

    /**
     * Gets unique threads used by a coroutine.
     */
    fun getUniqueThreads(label: String): Set<String> {
        return getThreadEvents(label).map { it.threadName }.toSet()
    }

    /**
     * Counts how many times a coroutine switched threads.
     */
    fun countThreadSwitches(label: String): Int {
        val threadEvents = getThreadEvents(label)
        if (threadEvents.size < 2) return 0

        var switches = 0
        for (i in 0 until threadEvents.size - 1) {
            if (threadEvents[i].threadName != threadEvents[i + 1].threadName) {
                switches++
            }
        }
        return switches
    }
}

