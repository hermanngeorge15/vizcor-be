package com.jh.proj.coroutineviz.models

import kotlinx.serialization.Serializable

/**
 * Represents a coroutine in the hierarchy tree projection.
 *
 * HierarchyNode is used by [ProjectionService] to build parent-child relationship
 * trees for visualization. Unlike [CoroutineNode], this model is serializable
 * and includes computed fields like children lists and timing information.
 *
 * @property id Unique coroutine identifier
 * @property parentId ID of parent coroutine, null for root coroutines
 * @property children List of child coroutine IDs
 * @property name Display name (label or generated)
 * @property scopeId ID of the owning VizScope
 * @property state Current state as a string
 * @property createdAtNanos Timestamp when coroutine was created
 * @property completedAtNanos Timestamp when coroutine finished, if applicable
 * @property dispatcherId ID of the assigned dispatcher
 * @property dispatcherName Human-readable dispatcher name
 * @property currentThreadId ID of thread currently executing, if any
 * @property currentThreadName Name of thread currently executing, if any
 * @property jobId Associated Job identifier
 * @property exceptionType Type of exception if failed
 * @property exceptionMessage Exception message if failed
 */
@Serializable
data class HierarchyNode(
    val id: String,                          // coroutineId
    val parentId: String?,                   // parentCoroutineId
    val children: List<String> = emptyList(), // child coroutine IDs
    val name: String,                        // label or generated name
    val scopeId: String,
    val state: String,                       // "CREATED", "RUNNING", "SUSPENDED", "COMPLETED", "CANCELLED", "FAILED"
    val createdAtNanos: Long,
    val completedAtNanos: Long? = null,
    val dispatcherId: String? = null,
    val dispatcherName: String? = null,
    val currentThreadId: Long? = null,
    val currentThreadName: String? = null,
    val jobId: String,
    val exceptionType: String? = null,       // If failed
    val exceptionMessage: String? = null
)
