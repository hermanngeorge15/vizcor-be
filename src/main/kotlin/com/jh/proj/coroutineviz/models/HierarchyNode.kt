package com.jh.proj.coroutineviz.models

import kotlinx.serialization.Serializable

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
