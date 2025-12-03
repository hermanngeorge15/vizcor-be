package com.jh.proj.coroutineviz.checksystem





/**
 * Result of an ordering/validation check.
 *
 * Either Success or Failure with detailed error information.
 *
 * Usage:
 *  between events.
 *
 * Use cases:
 * - A must happen before B (partial order)
 * - Chain: A < B < C < D
 * - Group A happens before Group B
 * - Time window constraints
 *
 * Usage:
 *  * val checker = HappensBeforeChecker(recorder)
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
 * val result = validator.check()
 * result.assertSuccess() // Throws if failure
 * if (result.isSuccess()) {
 * println("Validation passed!")
 * }
*/
sealed class OrderResult {
    /**
     * Validation succeeded.
     */
    object Success : OrderResult()

    /**
     * Validation failed with detailed error information.
     */
    data class Failure(
        val message: String,
        val expected: Any? = null,
        val actual: Any? = null,
        val context: Map<String, Any> = emptyMap()
    ) : OrderResult() {

        /**
         * Returns a formatted error message with all details.
         */
        fun detailedMessage(): String = buildString {
            appendLine(message)

            if (expected != null) {
                appendLine("  Expected: $expected")
            }

            if (actual != null) {
                appendLine("  Actual: $actual")
            }

            if (context.isNotEmpty()) {
                appendLine("  Context:")
                context.forEach { (key, value) ->
                    appendLine("    - $key: $value")
                }
            }
        }
    }

    /**
     * Throws AssertionError if this is a Failure.
     */
    fun assertSuccess(prefix: String = "") {
        if (this is Failure) {
            val fullMessage = if (prefix.isNotEmpty()) {
                "$prefix:\n${detailedMessage()}"
            } else {
                detailedMessage()
            }
            throw AssertionError(fullMessage)
        }
    }

    /**
     * Returns true if this is Success.
     */
    fun isSuccess() = this is Success

    /**
     * Returns true if this is Failure.
     */
    fun isFailure() = this is Failure

    /**
     * Gets the failure message if this is a Failure, null otherwise.
     */
    fun getFailureMessage(): String? = (this as? Failure)?.message

    /**
     * Gets detailed failure message if this is a Failure, null otherwise.
     */
    fun getDetailedFailureMessage(): String? = (this as? Failure)?.detailedMessage()

    // ========== Combinators ==========

    /**
     * Logical AND: both must succeed.
     */
    infix fun and(other: OrderResult): OrderResult = when {
        this is Failure -> this
        other is Failure -> other
        else -> Success
    }

    /**
     * Logical OR: at least one must succeed.
     */
    infix fun or(other: OrderResult): OrderResult = when {
        this is Success -> this
        other is Success -> other
        else -> other // Return last failure
    }

    /**
     * Maps Success to another result, preserves Failure.
     */
    fun andThen(block: () -> OrderResult): OrderResult = when (this) {
        is Success -> block()
        is Failure -> this
    }
}