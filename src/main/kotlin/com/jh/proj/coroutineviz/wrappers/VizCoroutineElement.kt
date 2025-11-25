package com.jh.proj.coroutineviz.wrappers

import kotlin.coroutines.CoroutineContext

data class VizCoroutineElement(
    val coroutineId: String,
    val jobId: String,
    val parentCoroutineId: String?,
    val scopeId: String,
    val label: String?
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<VizCoroutineElement>

    override val key: CoroutineContext.Key<*> = Key
}