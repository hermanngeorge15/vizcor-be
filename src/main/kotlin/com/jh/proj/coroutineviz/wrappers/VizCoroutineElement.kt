package com.jh.proj.coroutineviz.wrappers

import kotlin.coroutines.CoroutineContext

/**
 * CoroutineContext element that carries visualization identifiers so events can be attributed
 * to the correct coroutine/job/scope hierarchy.
 */
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
