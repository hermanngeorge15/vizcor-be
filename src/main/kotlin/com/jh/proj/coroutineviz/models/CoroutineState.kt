package com.jh.proj.coroutineviz.models

/**
 * Lifecycle states for a coroutine.
 *
 * These states represent the complete lifecycle of a coroutine from creation
 * to termination. The state machine follows this general flow:
 *
 * ```
 * CREATED → ACTIVE ⇄ SUSPENDED → WAITING_FOR_CHILDREN → COMPLETED
 *                                                     ↘ CANCELLED
 *                                                     ↘ FAILED
 * ```
 */
enum class CoroutineState {
    /** Coroutine has been created but not yet started. */
    CREATED,

    /** Coroutine is actively executing code on a thread. */
    ACTIVE,

    /** Coroutine is suspended, waiting to be resumed (e.g., during delay or await). */
    SUSPENDED,

    /** Coroutine's own code has finished, but it's waiting for child coroutines to complete. */
    WAITING_FOR_CHILDREN,

    /** Coroutine has completed successfully (including all children). */
    COMPLETED,

    /** Coroutine was cancelled (by parent, explicit cancellation, or child failure). */
    CANCELLED,

    /** Coroutine failed with an exception. */
    FAILED,
}
