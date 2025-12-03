@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * InstrumentedStateFlow wraps a MutableStateFlow to emit visualization events.
 * 
 * Features:
 * - Tracks value changes with old/new value comparison
 * - Monitors subscriber count
 * - Tracks all state updates
 * - Thread-safe subscriber tracking
 * 
 * @param T The type of state values
 * @param delegate The underlying MutableStateFlow
 * @param session The VizSession for event tracking
 * @param flowId Unique identifier for this StateFlow
 * @param label Optional human-readable label
 */
class InstrumentedStateFlow<T>(
    private val delegate: MutableStateFlow<T>,
    private val session: VizSession,
    val flowId: String,
    val label: String? = null
) : MutableStateFlow<T> {

    private val subscriberCount = AtomicInteger(0)
    
    private val ctx: FlowEventContext by lazy {
        FlowEventContext(
            session = session,
            flowId = flowId,
            flowType = "StateFlow",
            label = label
        )
    }

    init {
        // Emit FlowCreated for StateFlow
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = "unknown",
                flowId = flowId,
                flowType = "StateFlow",
                label = label,
                scopeId = null
            )
        )
        logger.debug("StateFlow created: flowId=$flowId, initialValue=${delegate.value}")
    }

    // ========================================================================
    // StateFlow Properties
    // ========================================================================

    override val replayCache: List<T>
        get() = delegate.replayCache

    override val subscriptionCount: StateFlow<Int>
        get() = MutableStateFlow(subscriberCount.get())

    // ========================================================================
    // MutableStateFlow - Value Updates
    // ========================================================================

    override fun compareAndSet(expect: T, update: T): Boolean {
        val oldValue = delegate.value
        val result = delegate.compareAndSet(expect, update)
        
        if (result) {
            emitValueChanged(oldValue, update)
        }
        
        return result
    }

    override var value: T
        get() = delegate.value
        set(newValue) {
            val oldValue = delegate.value
            delegate.value = newValue
            
            if (oldValue != newValue) {
                emitValueChanged(oldValue, newValue)
            }
        }

    override suspend fun emit(value: T) {
        val oldValue = delegate.value
        delegate.emit(value)
        
        if (oldValue != value) {
            emitValueChanged(oldValue, value)
        }
    }

    override fun tryEmit(value: T): Boolean {
        val oldValue = delegate.value
        val result = delegate.tryEmit(value)
        
        if (result && oldValue != value) {
            emitValueChanged(oldValue, value)
        }
        
        return result
    }

    override fun resetReplayCache() {
        delegate.resetReplayCache()
    }

    private fun emitValueChanged(oldValue: T, newValue: T) {
        val coroutineId = runCatching { 
            kotlinx.coroutines.runBlocking { 
                currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
            } 
        }.getOrNull()
        
        session.send(
            StateFlowValueChanged(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                flowId = flowId,
                oldValuePreview = oldValue?.toString()?.take(200) ?: "null",
                newValuePreview = newValue?.toString()?.take(200) ?: "null",
                valueType = newValue?.javaClass?.simpleName ?: "null",
                subscriberCount = subscriberCount.get(),
                coroutineId = coroutineId,
                label = label
            )
        )
        
        logger.debug("StateFlow value changed: flowId=$flowId, old=$oldValue, new=$newValue, subscribers=${subscriberCount.get()}")
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

        logger.debug("StateFlow collector subscribed: flowId=$flowId, collectorId=$collectorId, subscribers=$currentSubscribers")

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
            
            logger.debug("StateFlow collector unsubscribed: flowId=$flowId, collectorId=$collectorId, subscribers=$remainingSubscribers")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstrumentedStateFlow::class.java)
    }
}

/**
 * Create an instrumented MutableStateFlow.
 */
fun <T> VizSession.vizStateFlow(
    initialValue: T,
    label: String? = null
): InstrumentedStateFlow<T> {
    val flowId = "stateflow-${nextSeq()}"
    return InstrumentedStateFlow(
        delegate = MutableStateFlow(initialValue),
        session = this,
        flowId = flowId,
        label = label
    )
}

/**
 * Wrap an existing MutableStateFlow with instrumentation.
 */
fun <T> MutableStateFlow<T>.instrumented(
    session: VizSession,
    label: String? = null
): InstrumentedStateFlow<T> {
    val flowId = "stateflow-${session.nextSeq()}"
    return InstrumentedStateFlow(
        delegate = this,
        session = session,
        flowId = flowId,
        label = label
    )
}
