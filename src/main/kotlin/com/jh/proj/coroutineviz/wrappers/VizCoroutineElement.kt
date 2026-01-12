package com.jh.proj.coroutineviz.wrappers

import kotlin.coroutines.CoroutineContext

/**
 * CoroutineContext element that carries visualization metadata through the coroutine hierarchy.
 *
 * VizCoroutineElement is automatically added to the coroutine context by [VizScope.vizLaunch]
 * and [VizScope.vizAsync]. It enables:
 * - Parent-child relationship tracking (via [parentCoroutineId])
 * - Event attribution to the correct coroutine
 * - Scope membership tracking
 *
 * Access the current element from any coroutine context:
 * ```kotlin
 * val element = currentCoroutineContext()[VizCoroutineElement]
 * println("Current coroutine: ${element?.coroutineId}")
 * ```
 *
 * @property coroutineId Unique identifier for this coroutine
 * @property jobId ID of the associated Job
 * @property parentCoroutineId ID of the parent coroutine, null for root coroutines
 * @property scopeId ID of the owning VizScope
 * @property label Optional human-readable label for this coroutine
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
