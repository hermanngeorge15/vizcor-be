package com.jh.proj.coroutineviz.models

/**
 * Current state snapshot of all coroutines in a session.
 *
 * RuntimeSnapshot provides a mutable view of the current coroutine states,
 * updated incrementally by [EventApplier] as events are processed. This is
 * the "current state" in an event-sourcing architecture.
 *
 * The snapshot is optimized for fast lookups by coroutine ID, making it
 * efficient to query the current state of any coroutine.
 *
 * @property coroutines Map of coroutine ID to [CoroutineNode] state
 */
class RuntimeSnapshot {
    val coroutines: MutableMap<String, CoroutineNode> = mutableMapOf()
}
