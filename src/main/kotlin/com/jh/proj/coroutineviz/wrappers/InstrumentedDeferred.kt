package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.SuspensionPoint
import com.jh.proj.coroutineviz.events.coroutine.CoroutineResumed
import com.jh.proj.coroutineviz.events.coroutine.CoroutineSuspended
import com.jh.proj.coroutineviz.events.deferred.DeferredAwaitCompleted
import com.jh.proj.coroutineviz.events.deferred.DeferredAwaitStarted
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext

@OptIn(InternalForInheritanceCoroutinesApi::class)
/**
 * Deferred wrapper that emits await lifecycle events and the awaiter's
 * suspension/resumption for visualization.
 */
class InstrumentedDeferred<T>(
    private val delegate: Deferred<T>,
    private val session: VizSession,
    private val deferredId: String,
    private val coroutineId: String,
    private val jobId: String,
    private val parentCoroutineId: String?,
    private val scopeId: String,
    private val label: String?
) : Deferred<T> by delegate {
    override suspend fun await(): T {
        val awaiterId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId

        // Emit DeferredAwaitStarted event
        session.sent(
            DeferredAwaitStarted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                deferredId = deferredId,
                coroutineId = coroutineId,
                awaiterId = awaiterId,
                scopeId = scopeId,
                label = label
            )
        )

        // Emit suspension point (only if awaiter exists)
        if (awaiterId != null) {
            val suspensionPoint = SuspensionPoint.capture("await")
            session.sent(
                CoroutineSuspended(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = awaiterId,
                    jobId = currentCoroutineContext()[VizCoroutineElement]?.jobId ?: "unknown",
                    parentCoroutineId = currentCoroutineContext()[VizCoroutineElement]?.parentCoroutineId,
                    scopeId = scopeId,
                    label = currentCoroutineContext()[VizCoroutineElement]?.label,
                    reason = "await",
                    durationMillis = null,
                    suspensionPoint = suspensionPoint
                )
            )
        }

        // Actually await the result
        val result = delegate.await()

        // Emit resumption (only if awaiter exists)
        if (awaiterId != null) {
            session.sent(
                CoroutineResumed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = awaiterId,
                    jobId = currentCoroutineContext()[VizCoroutineElement]?.jobId ?: "unknown",
                    parentCoroutineId = currentCoroutineContext()[VizCoroutineElement]?.parentCoroutineId,
                    scopeId = scopeId,
                    label = currentCoroutineContext()[VizCoroutineElement]?.label
                )
            )
        }

        // Emit DeferredAwaitCompleted
        session.sent(
            DeferredAwaitCompleted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                deferredId = deferredId,
                coroutineId = coroutineId,
                awaiterId = awaiterId,
                scopeId = scopeId,
                label = label
            )
        )

        return result
    }
}
