package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.CoroutineBodyCompleted
import com.jh.proj.coroutineviz.events.CoroutineCancelled
import com.jh.proj.coroutineviz.events.CoroutineCompleted
import com.jh.proj.coroutineviz.events.CoroutineFailed
import com.jh.proj.coroutineviz.events.CoroutineSuspended
import com.jh.proj.coroutineviz.events.DeferredValueAvailable
import com.jh.proj.coroutineviz.events.InstrumentedDeferred
import com.jh.proj.coroutineviz.events.SuspensionPoint
import com.jh.proj.coroutineviz.events.ThreadAssigned
import com.jh.proj.coroutineviz.session.EventContext
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.session.coroutineCreated
import com.jh.proj.coroutineviz.session.coroutineResumed
import com.jh.proj.coroutineviz.session.coroutineStarted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.*
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
    private val session: VizSession,
    context: CoroutineContext = EmptyCoroutineContext,
    val scopeId: String = "scope-${session.nextSeq()}"
) : CoroutineScope {

    // Independent scope - not tied to caller's scope
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

        // Launch with the viz element in context
        val job = this.launch(context + vizElement) {
            // ✅ EMIT: CoroutineCreated - clean DSL
            session.send(ctx.coroutineCreated())

            // ✅ EMIT: CoroutineStarted - clean DSL
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
            session.sent(
                CoroutineBodyCompleted(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = coroutineId,
                    jobId = jobId,
                    parentCoroutineId = parentCoroutineId,
                    scopeId = scopeId,
                    label = label
                )
            )

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
//            if (!bodyTerminalEventEmitted.get()) {
            when {
                cause == null -> {
                    // Normal completion - body finished and all children completed successfully
                    session.send(
                        CoroutineCompleted(
                            sessionId = session.sessionId,
                            seq = session.nextSeq(),
                            tsNanos = System.nanoTime(),
                            coroutineId = coroutineId,
                            jobId = jobId,
                            parentCoroutineId = parentCoroutineId,
                            scopeId = scopeId,
                            label = label
                        )
                    )
                }

                cause is CancellationException -> {
                    // Cancellation - could be:
                    // - Child failed (structured concurrency cancellation)
                    // - Explicit cancellation
                    // - Parent cancelled
                    session.send(
                        CoroutineCancelled(
                            sessionId = session.sessionId,
                            seq = session.nextSeq(),
                            tsNanos = System.nanoTime(),
                            coroutineId = coroutineId,
                            jobId = jobId,
                            parentCoroutineId = parentCoroutineId,
                            scopeId = scopeId,
                            label = label,
                            cause = cause.message ?: "Cancelled"
                        )
                    )
                }

                else -> {
                    // Failure - own code threw an exception
                    // Note: In practice, this rarely happens because coroutineScope
                    // converts exceptions to CancellationException
                    session.send(
                        CoroutineFailed(
                            sessionId = session.sessionId,
                            seq = session.nextSeq(),
                            tsNanos = System.nanoTime(),
                            coroutineId = coroutineId,
                            jobId = jobId,
                            parentCoroutineId = parentCoroutineId,
                            scopeId = scopeId,
                            label = label,
                            exceptionType = cause::class.simpleName ?: "Unknown",
                            message = cause.message,
                            stackTrace = cause.stackTrace.take(10).map { it.toString() }
                        )
                    )
                }
            }
//            }
        }

//        // Wrap the job with VizJob for operation tracking
//        return job.toVizJob(
//            session = session,
//            coroutineId = coroutineId,
//            jobId = jobId,
//            parentCoroutineId = parentCoroutineId,
//            scopeId = scopeId,
//            label = label
//        )

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

        // Track terminal events (similar to vizLaunch)
        val bodyTerminalEventEmitted = AtomicBoolean(false)

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

            try {
                // Execute block in a coroutineScope to ensure structured concurrency
                val result = kotlinx.coroutines.coroutineScope {
                    block()
                }

                // Emit body completed
                session.sent(
                    CoroutineBodyCompleted(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        coroutineId = coroutineId,
                        jobId = jobId,
                        parentCoroutineId = parentCoroutineId,
                        scopeId = scopeId,
                        label = label
                    )
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

            } catch (e: Throwable) {
                // Same as vizLaunch: let invokeOnCompletion handle terminal event emission
                throw e
            }
        }

        // Register completion handler (same logic as vizLaunch)
        // session.send() is NOT suspend, so we can call it directly
        deferred.invokeOnCompletion { cause ->
            // Only emit terminal event if we haven't already emitted one
            if (!bodyTerminalEventEmitted.get()) {
                when {
                    cause == null -> {
                        // Normal completion
                        session.send(
                            CoroutineCompleted(
                                sessionId = session.sessionId,
                                seq = session.nextSeq(),
                                tsNanos = System.nanoTime(),
                                coroutineId = coroutineId,
                                jobId = jobId,
                                parentCoroutineId = parentCoroutineId,
                                scopeId = scopeId,
                                label = label
                            )
                        )
                    }

                    cause is CancellationException -> {
                        // Cancellation (child failed, explicit cancellation, etc.)
                        session.send(
                            CoroutineCancelled(
                                sessionId = session.sessionId,
                                seq = session.nextSeq(),
                                tsNanos = System.nanoTime(),
                                coroutineId = coroutineId,
                                jobId = jobId,
                                parentCoroutineId = parentCoroutineId,
                                scopeId = scopeId,
                                label = label,
                                cause = cause.message ?: "Cancelled"
                            )
                        )
                    }

                    else -> {
                        // Failure - own code threw an exception
                        session.send(
                            CoroutineFailed(
                                sessionId = session.sessionId,
                                seq = session.nextSeq(),
                                tsNanos = System.nanoTime(),
                                coroutineId = coroutineId,
                                jobId = jobId,
                                parentCoroutineId = parentCoroutineId,
                                scopeId = scopeId,
                                label = label,
                                exceptionType = cause::class.simpleName ?: "Unknown",
                                message = cause.message,
                                stackTrace = cause.stackTrace.take(10).map { it.toString() }
                            )
                        )
                    }
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
                CoroutineSuspended(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = coroutineElement.coroutineId,
                    jobId = coroutineElement.jobId,
                    parentCoroutineId = coroutineElement.parentCoroutineId,
                    scopeId = coroutineElement.scopeId,
                    label = coroutineElement.label,
                    reason = "delay",
                    durationMillis = timeMillis,
                    suspensionPoint = suspensionPoint
                )
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