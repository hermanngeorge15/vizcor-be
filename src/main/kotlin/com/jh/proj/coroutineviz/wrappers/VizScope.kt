package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.DeferredValueAvailable
import com.jh.proj.coroutineviz.events.FlowCreated
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
    private val session: VizSession,
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

    // ========================================================================
    // Flow Builders
    // ========================================================================

    /**
     * Create an instrumented Flow with visualization tracking.
     * 
     * This function wraps a flow builder to emit events for:
     * - Flow creation
     * - Collection start/complete/cancel
     * - Value emissions with sequence numbers
     * - Operator applications
     * 
     * Usage:
     * ```kotlin
     * val flow = scope.vizFlow("my-flow") {
     *     emit(1)
     *     emit(2)
     *     emit(3)
     * }
     * 
     * flow.collect { println(it) }
     * ```
     * 
     * @param label Optional human-readable label for the flow
     * @param block The flow builder block
     * @return An InstrumentedFlow that tracks all operations
     */
    fun <T> vizFlow(
        label: String? = null,
        block: suspend FlowCollector<T>.() -> Unit
    ): InstrumentedFlow<T> {
        val flowId = "flow-${session.nextSeq()}"
        val coroutineId = runCatching { 
            kotlinx.coroutines.runBlocking { 
                currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
            } 
        }.getOrNull()
        
        // Emit FlowCreated event
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = coroutineId ?: "unknown",
                flowId = flowId,
                flowType = "Cold",
                label = label,
                scopeId = scopeId
            )
        )
        
        // Create the underlying flow
        val underlyingFlow = flow(block)
        
        // Wrap it with instrumentation
        return InstrumentedFlow(
            delegate = underlyingFlow,
            session = session,
            flowId = flowId,
            flowType = "Cold",
            label = label
        )
    }

    /**
     * Wrap an existing Flow with instrumentation.
     * 
     * Use this to add visualization tracking to any existing Flow.
     * 
     * @param existingFlow The Flow to wrap
     * @param label Optional human-readable label
     * @return An InstrumentedFlow that tracks all operations
     */
    fun <T> vizWrap(
        existingFlow: Flow<T>,
        label: String? = null
    ): InstrumentedFlow<T> {
        val flowId = "flow-${session.nextSeq()}"
        val coroutineId = runCatching { 
            kotlinx.coroutines.runBlocking { 
                currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
            } 
        }.getOrNull()
        
        // Emit FlowCreated event
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = coroutineId ?: "unknown",
                flowId = flowId,
                flowType = "Cold",
                label = label,
                scopeId = scopeId
            )
        )
        
        return InstrumentedFlow(
            delegate = existingFlow,
            session = session,
            flowId = flowId,
            flowType = "Cold",
            label = label
        )
    }

    /**
     * Create an instrumented MutableStateFlow.
     * 
     * StateFlow is a hot Flow that:
     * - Always has a current value
     * - Replays the current value to new collectors
     * - Emits only when value changes (distinctUntilChanged)
     * 
     * @param initialValue The initial value of the StateFlow
     * @param label Optional human-readable label
     * @return An InstrumentedStateFlow with visualization tracking
     */
    fun <T> vizStateFlow(
        initialValue: T,
        label: String? = null
    ): InstrumentedStateFlow<T> {
        val flowId = "stateflow-${session.nextSeq()}"
        return InstrumentedStateFlow(
            delegate = MutableStateFlow(initialValue),
            session = session,
            flowId = flowId,
            label = label
        )
    }

    /**
     * Create an instrumented MutableSharedFlow.
     * 
     * SharedFlow is a hot Flow that:
     * - Can have multiple subscribers
     * - Supports replay cache for late subscribers
     * - Can buffer emissions with configurable overflow strategy
     * 
     * @param replay Number of values to replay to new subscribers
     * @param extraBufferCapacity Extra buffer capacity beyond replay
     * @param onBufferOverflow Strategy for handling buffer overflow
     * @param label Optional human-readable label
     * @return An InstrumentedSharedFlow with visualization tracking
     */
    fun <T> vizSharedFlow(
        replay: Int = 0,
        extraBufferCapacity: Int = 0,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
        label: String? = null
    ): InstrumentedSharedFlow<T> {
        val flowId = "sharedflow-${session.nextSeq()}"
        return InstrumentedSharedFlow(
            delegate = MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow),
            session = session,
            flowId = flowId,
            label = label,
            extraBufferCapacity = extraBufferCapacity
        )
    }

    /**
     * Create an instrumented interval Flow that emits values at fixed intervals.
     * 
     * Useful for testing timing-related Flow behavior.
     * 
     * @param periodMillis The interval between emissions in milliseconds
     * @param initialDelayMillis Optional initial delay before first emission
     * @param label Optional human-readable label
     * @return An InstrumentedFlow that emits Long values at intervals
     */
    fun vizInterval(
        periodMillis: Long,
        initialDelayMillis: Long = 0,
        label: String? = null
    ): InstrumentedFlow<Long> {
        val flowId = "flow-interval-${session.nextSeq()}"
        
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = "unknown",
                flowId = flowId,
                flowType = "Interval",
                label = label ?: "interval(${periodMillis}ms)",
                scopeId = scopeId
            )
        )
        
        val intervalFlow = flow {
            if (initialDelayMillis > 0) {
                delay(initialDelayMillis)
            }
            var counter = 0L
            while (true) {
                emit(counter++)
                delay(periodMillis)
            }
        }
        
        return InstrumentedFlow(
            delegate = intervalFlow,
            session = session,
            flowId = flowId,
            flowType = "Interval",
            label = label ?: "interval(${periodMillis}ms)"
        )
    }

    /**
     * Create an instrumented Flow from a range.
     * 
     * @param range The range of values to emit
     * @param delayMillis Optional delay between emissions
     * @param label Optional human-readable label
     * @return An InstrumentedFlow that emits values from the range
     */
    fun vizRange(
        range: IntRange,
        delayMillis: Long = 0,
        label: String? = null
    ): InstrumentedFlow<Int> {
        val flowId = "flow-range-${session.nextSeq()}"
        
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = "unknown",
                flowId = flowId,
                flowType = "Range",
                label = label ?: "range(${range.first}..${range.last})",
                scopeId = scopeId
            )
        )
        
        val rangeFlow = flow {
            for (value in range) {
                emit(value)
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
            }
        }
        
        return InstrumentedFlow(
            delegate = rangeFlow,
            session = session,
            flowId = flowId,
            flowType = "Range",
            label = label ?: "range(${range.first}..${range.last})"
        )
    }

    /**
     * Create an instrumented Flow from a list of values.
     * 
     * @param values The values to emit
     * @param delayMillis Optional delay between emissions
     * @param label Optional human-readable label
     * @return An InstrumentedFlow that emits the values
     */
    fun <T> vizFlowOf(
        vararg values: T,
        delayMillis: Long = 0,
        label: String? = null
    ): InstrumentedFlow<T> {
        val flowId = "flow-of-${session.nextSeq()}"
        
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = "unknown",
                flowId = flowId,
                flowType = "FlowOf",
                label = label ?: "flowOf(${values.size} items)",
                scopeId = scopeId
            )
        )
        
        val listFlow = flow {
            for (value in values) {
                emit(value)
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
            }
        }
        
        return InstrumentedFlow(
            delegate = listFlow,
            session = session,
            flowId = flowId,
            flowType = "FlowOf",
            label = label ?: "flowOf(${values.size} items)"
        )
    }
}