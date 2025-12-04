package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.DeferredValueAvailable
import com.jh.proj.coroutineviz.events.InstrumentedDeferred
import com.jh.proj.coroutineviz.events.SuspensionPoint
import com.jh.proj.coroutineviz.events.ThreadAssigned
import com.jh.proj.coroutineviz.session.EventContext
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.session.coroutineBodyCompleted
import com.jh.proj.coroutineviz.session.coroutineCancelled
import com.jh.proj.coroutineviz.session.coroutineCompleted
import com.jh.proj.coroutineviz.session.coroutineCreated
import com.jh.proj.coroutineviz.session.coroutineFailed
import com.jh.proj.coroutineviz.session.coroutineResumed
import com.jh.proj.coroutineviz.session.coroutineStarted
import com.jh.proj.coroutineviz.session.coroutineSuspended
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * VizScope creates an independent coroutine hierarchy for visualization.
 *
 * It maintains structured concurrency within its own scope, independent of the caller's scope.
 * This allows multiple visualization sessions to run concurrently without interfering with each other.
 *
 * With the default Job (not SupervisorJob), child failures will cancel parent and siblings,
 * demonstrating proper structured concurrency behavior. Use SupervisorJob if you want
 * children to fail independently.
 *
 * @param session The visualization session for tracking events
 * @param context Optional coroutine context (dispatcher, job, etc.) for customization
 * @param scopeId Unique identifier for this scope
 */
class VizScope(
    internal val session: VizSession,
    context: CoroutineContext = EmptyCoroutineContext,
    val scopeId: String = "scope-${session.nextSeq()}"
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = context + CoroutineName("VizScope-$scopeId")

    /**
     * Launch a coroutine with visualization tracking while maintaining structured concurrency.
     *
     * This function:
     * - Launches on the VizScope's independent coroutine scope
     * - Tracks parent-child relationships through VizCoroutineElement in context
     * - Ensures parents wait for all children to complete (structured concurrency)
     * - Emits events in the correct order (children complete before parents)
     *
     * @param label Optional label for the coroutine
     * @param context Additional coroutine context to merge
     * @param block The coroutine body to execute
     * @return VizJob handle for the launched coroutine with event tracking
     */
    suspend fun vizLaunch(
        label: String? = null,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend VizScope.() -> Unit,
    ): Job {
        // Check if we're already inside a vizLaunch (nested call)
        // Use currentCoroutineContext() to get the CURRENT coroutine's context, not VizScope's
        val parentElement = currentCoroutineContext()[VizCoroutineElement]
        val parentCoroutineId = parentElement?.coroutineId

        val coroutineId = "coroutine-${session.nextSeq()}"
        val jobId = "job-${coroutineId}"

        val vizElement = VizCoroutineElement(
            coroutineId = coroutineId,
            label = label,
            parentCoroutineId = parentCoroutineId,
            scopeId = scopeId,
            jobId = jobId,
        )

        // ✅ Create EventContext once for this coroutine
        val ctx = EventContext(
            session = session,
            coroutineId = coroutineId,
            jobId = jobId,
            parentCoroutineId = parentCoroutineId,
            scopeId = scopeId,
            label = label
        )

        // Check if we're in a nested context (inside another vizLaunch/vizAsync)
        // If so, use current scope; otherwise use VizScope
        val targetScope = currentCoroutineContext()[VizCoroutineElement]?.let {
            CoroutineScope(currentCoroutineContext())
        } ?: this


        // Launch with the viz element in context
        val job = targetScope.launch(context + vizElement) {
            // ✅ EMIT: CoroutineCreated
            session.send(ctx.coroutineCreated())

            // ✅ EMIT: CoroutineStarted
            session.send(ctx.coroutineStarted())

            // EMIT: ThreadAssigned
            session.sent(
                ThreadAssigned(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = coroutineId,
                    jobId = jobId,
                    parentCoroutineId = parentCoroutineId,
                    scopeId = scopeId,
                    label = label,
                    threadId = Thread.currentThread().threadId(),
                    threadName = Thread.currentThread().name,
                    dispatcherName = coroutineContext[ContinuationInterceptor]?.toString() ?: "Unknown"
                )
            )

            block()

            // Emit CoroutineBodyCompleted - the coroutine's own code has finished
            // AND all children have completed (due to coroutineScope above)
            session.sent(ctx.coroutineBodyCompleted())

        }

        // Register completion handler - this fires AFTER the job and all children complete
        // This is crucial for detecting the FINAL state of the Job:
        // 1. cause == null -> Normal completion
        // 2. cause is CancellationException -> Cancelled (child failed, explicit cancellation, etc.)
        // 3. cause is other Throwable -> Failed (but in practice, this is rare for jobs)
        job.invokeOnCompletion { cause ->
            // invokeOnCompletion is NOT a suspend function, but session.send() is also NOT suspend!
            // We can call it directly without launching a coroutine
            // Only emit terminal event if we haven't already emitted one
            when {
                cause == null -> {
                    // Normal completion - body finished and all children completed successfully
                    session.send(
                        ctx.coroutineCompleted()
                    )
                }

                cause !is CancellationException && cause.message?.contains(ctx.label ?: "unknown") == true -> {
                    // Failure - own code threw an exception
                    // Note: In practice, this rarely happens because coroutineScope
                    // converts exceptions to CancellationException
                    session.send(ctx.coroutineFailed(cause::class.simpleName, cause.message))
                }

                cause is CancellationException || job.isCancelled -> {
                    // Cancellation - could be:
                    // - Child failed (structured concurrency cancellation)
                    // - Explicit cancellation
                    // - Parent cancelled
                    session.send(ctx.coroutineCancelled(cause.message ?: "CancellationException"))
                }

                else -> {
                    // Failure - own code threw an exception
                    // Note: In practice, this rarely happens because coroutineScope
                    // converts exceptions to CancellationException
                    throw IllegalArgumentException("It is not correct state")
                }
            }
        }
        return job
    }

    suspend fun <T> vizAsync(
        label: String? = null,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend VizScope.() -> T
    ): InstrumentedDeferred<T> {
        val parentElement = currentCoroutineContext()[VizCoroutineElement]
        val parentCoroutineId = parentElement?.coroutineId

        val coroutineId = "coroutine-${session.nextSeq()}"
        val jobId = "job-${coroutineId}"
        val deferredId = "deferred-${coroutineId}"

        val vizElement = VizCoroutineElement(
            coroutineId = coroutineId,
            label = label,
            parentCoroutineId = parentCoroutineId,
            scopeId = scopeId,
            jobId = jobId
        )

        val ctx = EventContext(
            session = session,
            coroutineId = coroutineId,
            jobId = jobId,
            parentCoroutineId = parentCoroutineId,
            scopeId = scopeId,
            label = label
        )

        // Check if we're in a nested context (inside another vizLaunch/vizAsync)
        // If so, use current scope; otherwise use VizScope
        val targetScope = currentCoroutineContext()[VizCoroutineElement]?.let {
            CoroutineScope(currentCoroutineContext())
        } ?: this

        // Use async{} instead of launch{}
        val deferred = targetScope.async(context + vizElement) {
            // Emit lifecycle events (similar to vizLaunch)
            session.send(ctx.coroutineCreated())
            session.send(ctx.coroutineStarted())

            session.sent(
                ThreadAssigned(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = coroutineId,
                    jobId = jobId,
                    parentCoroutineId = parentCoroutineId,
                    scopeId = scopeId,
                    label = label,
                    threadId = Thread.currentThread().threadId(),
                    threadName = Thread.currentThread().name,
                    dispatcherName = coroutineContext[ContinuationInterceptor]?.toString() ?: "Unknown"
                )
            )
            // Execute block
            val result = block()

            // Emit body completed
            session.sent(
                ctx.coroutineBodyCompleted()
            )

            // Emit deferred value available
            session.sent(
                DeferredValueAvailable(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = coroutineId,
                    jobId = jobId,
                    parentCoroutineId = parentCoroutineId,
                    scopeId = scopeId,
                    label = label,
                    deferredId = deferredId
                )
            )

            result
        }

        // Register completion handler (same logic as vizLaunch)
        // session.send() is NOT suspend, so we can call it directly
        deferred.invokeOnCompletion { cause ->
            // invokeOnCompletion is NOT a suspend function, but session.send() is also NOT suspend!
            // We can call it directly without launching a coroutine
            // Only emit terminal event if we haven't already emitted one
            when {
                cause == null -> {
                    // Normal completion - body finished and all children completed successfully
                    session.send(
                        ctx.coroutineCompleted()
                    )
                }

                cause !is CancellationException && cause.message?.contains(ctx.label ?: "unknown") == true -> {
                    // Failure - own code threw an exception
                    // Note: In practice, this rarely happens because coroutineScope
                    // converts exceptions to CancellationException
                    session.send(ctx.coroutineFailed(cause::class.simpleName, cause.message))
                }

                cause is CancellationException || deferred.isCancelled -> {
                    // Cancellation - could be:
                    // - Child failed (structured concurrency cancellation)
                    // - Explicit cancellation
                    // - Parent cancelled
                    session.send(ctx.coroutineCancelled(cause.message ?: "CancellationException"))
                }

                else -> {
                    // Failure - own code threw an exception
                    // Note: In practice, this rarely happens because coroutineScope
                    // converts exceptions to CancellationException
                    throw IllegalArgumentException("It is not correct state")
                }
            }
        }

        // Return wrapped deferred
        return InstrumentedDeferred(
            delegate = deferred,
            session = session,
            deferredId = deferredId,
            coroutineId = coroutineId,
            jobId = jobId,
            parentCoroutineId = parentCoroutineId,
            scopeId = scopeId,
            label = label
        )
    }

    suspend fun vizDelay(timeMillis: Long) {
        val coroutineElement = currentCoroutineContext()[VizCoroutineElement]

        if (coroutineElement != null) {
            // Create EventContext for this coroutine
            val ctx = EventContext(
                session = session,
                coroutineId = coroutineElement.coroutineId,
                jobId = coroutineElement.jobId,
                parentCoroutineId = coroutineElement.parentCoroutineId,
                scopeId = coroutineElement.scopeId,
                label = coroutineElement.label
            )

            // Capture suspension point with stack trace
            val suspensionPoint = SuspensionPoint.capture("delay", skipFrames = 3)

            // Emit suspension with detailed info
            session.sent(
                ctx.coroutineSuspended(reason = "delay", durationMillis = timeMillis, suspensionPoint = suspensionPoint)
            )

            // Actual suspension
            delay(timeMillis)

            // Emit resumption
            session.send(ctx.coroutineResumed())
        } else {
            // No instrumentation context, just delay
            delay(timeMillis)
        }
    }


    /**
     * Cancel all coroutines in this VizScope and wait for them to complete.
     */
    suspend fun cancelAndJoin() {
        coroutineContext[Job]?.cancelAndJoin()
    }

    /**
     * Cancel all coroutines in this VizScope.
     */
    fun cancel() {
        coroutineContext[Job]?.cancel()
    }
}