package com.jh.proj.coroutineviz.session

data class CoroutineNode(
    val id: String,
    val jobId: String,
    val parentId: String?,
    val scopeId: String,
    val label: String?,
    var state: CoroutineState,
    var threadId: Long? = null,          // Add
    var threadName: String? = null,      // Add
    var dispatcherName: String? = null   // Add
)
