package com.jh.proj.coroutineviz.models

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
