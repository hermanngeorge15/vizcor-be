package com.jh.proj.coroutineviz.models

/**
 * Represents the current state of a single coroutine in the runtime snapshot.
 *
 * CoroutineNode is the primary data structure used by [RuntimeSnapshot] to track
 * coroutine state. It is mutable to allow efficient updates as events are applied.
 *
 * @property id Unique identifier for this coroutine (e.g., "coroutine-1")
 * @property jobId Identifier for the associated Job (e.g., "job-coroutine-1")
 * @property parentId ID of the parent coroutine, or null if this is a root coroutine
 * @property scopeId ID of the VizScope that owns this coroutine
 * @property label Optional human-readable label for this coroutine
 * @property state Current lifecycle state of the coroutine
 * @property threadId ID of the thread currently executing this coroutine, if any
 * @property threadName Name of the thread currently executing this coroutine, if any
 * @property dispatcherName Name of the dispatcher this coroutine is using
 */
data class CoroutineNode(
    val id: String,
    val jobId: String,
    val parentId: String?,
    val scopeId: String,
    val label: String?,
    var state: CoroutineState,
    var threadId: Long? = null,
    var threadName: String? = null,
    var dispatcherName: String? = null
)
