@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * InstrumentedSharedFlow wraps a MutableSharedFlow to emit visualization events.
 * 
 * Features:
 * - Tracks all emissions with subscriber count
 * - Monitors subscriber lifecycle (subscribe/unsubscribe)
 * - Tracks replay cache state
 * - Handles buffer overflow scenarios
 * 
 * @param T The type of values emitted
 * @param delegate The underlying MutableSharedFlow
 * @param session The VizSession for event tracking
 * @param flowId Unique identifier for this SharedFlow
 * @param label Optional human-readable label
 * @param extraBufferCapacity The extra buffer capacity of the flow
 */
class InstrumentedSharedFlow<T>(
    private val delegate: MutableSharedFlow<T>,
    private val session: VizSession,
    val flowId: String,
    val label: String? = null,
    private val extraBufferCapacity: Int = 0
) : MutableSharedFlow<T> {

    private val subscriberCount = AtomicInteger(0)
    
    private val ctx: FlowEventContext by lazy {
        FlowEventContext(
            session = session,
            flowId = flowId,
            flowType = "SharedFlow",
            label = label
        )
    }

    init {
        // Emit FlowCreated for SharedFlow
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = "unknown",
                flowId = flowId,
                flowType = "SharedFlow",
                label = label,
                scopeId = null
            )
        )
        logger.debug("SharedFlow created: flowId=$flowId, replay=${delegate.replayCache.size}, extraBuffer=$extraBufferCapacity")
    }

    // ========================================================================
    // SharedFlow Properties
    // ========================================================================

    override val replayCache: List<T>
        get() = delegate.replayCache

    override val subscriptionCount: StateFlow<Int>
        get() = delegate.subscriptionCount

    // ========================================================================
    // MutableSharedFlow - Emission
    // ========================================================================

    override suspend fun emit(value: T) {
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
        
        delegate.emit(value)
        
        session.send(
            SharedFlowEmission(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                flowId = flowId,
                valuePreview = value?.toString()?.take(200) ?: "null",
                valueType = value?.javaClass?.simpleName ?: "null",
                subscriberCount = subscriberCount.get(),
                replayCache = delegate.replayCache.size,
                extraBufferCapacity = extraBufferCapacity,
                coroutineId = coroutineId,
                label = label
            )
        )
        
        logger.trace("SharedFlow emission: flowId=$flowId, value=$value, subscribers=${subscriberCount.get()}")
    }

    override fun tryEmit(value: T): Boolean {
        val result = delegate.tryEmit(value)
        
        if (result) {
            val coroutineId = runCatching { 
                kotlinx.coroutines.runBlocking { 
                    currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
                } 
            }.getOrNull()
            
            session.send(
                SharedFlowEmission(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = flowId,
                    valuePreview = value?.toString()?.take(200) ?: "null",
                    valueType = value?.javaClass?.simpleName ?: "null",
                    subscriberCount = subscriberCount.get(),
                    replayCache = delegate.replayCache.size,
                    extraBufferCapacity = extraBufferCapacity,
                    coroutineId = coroutineId,
                    label = label
                )
            )
        } else {
            // Buffer overflow - could not emit
            session.send(
                FlowBufferOverflow(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = "unknown",
                    flowId = flowId,
                    droppedValue = value?.toString()?.take(100),
                    bufferSize = delegate.replayCache.size + extraBufferCapacity,
                    overflowStrategy = "tryEmit_failed"
                )
            )
        }
        
        return result
    }

    override fun resetReplayCache() {
        delegate.resetReplayCache()
        logger.debug("SharedFlow replay cache reset: flowId=$flowId")
    }

    // ========================================================================
    // Flow Collection
    // ========================================================================

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val collectorId = "collector-${session.nextSeq()}"
        val coroutineContext = currentCoroutineContext()
        val coroutineId = coroutineContext[VizCoroutineElement]?.coroutineId ?: "unknown"

        // Track subscription
        val currentSubscribers = subscriberCount.incrementAndGet()
        
        session.send(
            SharedFlowSubscription(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                flowId = flowId,
                collectorId = collectorId,
                action = "subscribed",
                subscriberCount = currentSubscribers,
                coroutineId = coroutineId,
                label = label
            )
        )

        // Emit collection started
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))

        logger.debug("SharedFlow collector subscribed: flowId=$flowId, collectorId=$collectorId, subscribers=$currentSubscribers")

        var emissionCount = 0

        try {
            delegate.collect { value ->
                // Emit value event
                session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, emissionCount, value))
                emissionCount++
                collector.emit(value)
            }
        } catch (e: CancellationException) {
            session.send(
                ctx.copy(coroutineId = coroutineId).flowCollectionCancelled(
                    collectorId = collectorId,
                    reason = e.message ?: "CancellationException",
                    emittedCount = emissionCount
                )
            )
            throw e
        } finally {
            // Track unsubscription
            val remainingSubscribers = subscriberCount.decrementAndGet()
            
            session.send(
                SharedFlowSubscription(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = flowId,
                    collectorId = collectorId,
                    action = "unsubscribed",
                    subscriberCount = remainingSubscribers,
                    coroutineId = coroutineId,
                    label = label
                )
            )
            
            logger.debug("SharedFlow collector unsubscribed: flowId=$flowId, collectorId=$collectorId, subscribers=$remainingSubscribers")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstrumentedSharedFlow::class.java)
    }
}

/**
 * Create an instrumented MutableSharedFlow.
 */
fun <T> VizSession.vizSharedFlow(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    label: String? = null
): InstrumentedSharedFlow<T> {
    val flowId = "sharedflow-${nextSeq()}"
    return InstrumentedSharedFlow(
        delegate = MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow),
        session = this,
        flowId = flowId,
        label = label,
        extraBufferCapacity = extraBufferCapacity
    )
}

/**
 * Wrap an existing MutableSharedFlow with instrumentation.
 */
fun <T> MutableSharedFlow<T>.instrumented(
    session: VizSession,
    label: String? = null,
    extraBufferCapacity: Int = 0
): InstrumentedSharedFlow<T> {
    val flowId = "sharedflow-${session.nextSeq()}"
    return InstrumentedSharedFlow(
        delegate = this,
        session = session,
        flowId = flowId,
        label = label,
        extraBufferCapacity = extraBufferCapacity
    )
}

