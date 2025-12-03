package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration

/**
 * InstrumentedFlow wraps a Flow to emit visualization events for all operations.
 * 
 * Features:
 * - Tracks flow creation, collection, and completion
 * - Monitors value emissions with sequence numbers
 * - Detects backpressure and buffer overflows
 * - Tracks operator chains (map, filter, transform, etc.)
 * - Supports multiple collectors
 * 
 * @param T The type of values emitted by the flow
 * @param delegate The underlying Flow to wrap
 * @param session The VizSession for event tracking
 * @param flowId Unique identifier for this flow
 * @param flowType "Cold", "Hot", "StateFlow", "SharedFlow"
 * @param label Optional human-readable label
 * @param sourceFlowId ID of the upstream flow (for operator chains)
 * @param operatorIndex Position in the operator chain
 */
class InstrumentedFlow<T>(
    private val delegate: Flow<T>,
    private val session: VizSession,
    val flowId: String,
    val flowType: String = "Cold",
    val label: String? = null,
    private val sourceFlowId: String? = null,
    private val operatorIndex: Int = 0
) : Flow<T> {

    private val ctx: FlowEventContext by lazy {
        FlowEventContext(
            session = session,
            flowId = flowId,
            flowType = flowType,
            label = label,
            sourceFlowId = sourceFlowId,
            operatorIndex = operatorIndex
        )
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        val collectorId = "collector-${session.nextSeq()}"
        val coroutineContext = currentCoroutineContext()
        val coroutineId = coroutineContext[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()

        // Update context with coroutine info
        val collectionCtx = ctx.copy(coroutineId = coroutineId)

        logger.debug("Flow collection started: flowId=$flowId, collectorId=$collectorId, coroutineId=$coroutineId")

        // Emit collection started event
        session.send(collectionCtx.flowCollectionStarted(collectorId))

        var emissionCount = 0

        try {
            // Wrap the collector to intercept emissions
            delegate.collect { value ->
                // Emit value event BEFORE passing to collector
                session.send(collectionCtx.flowValueEmitted(collectorId, emissionCount, value))

                emissionCount++

                // Pass value to the actual collector
                collector.emit(value)

                logger.trace("Flow value emitted: flowId=$flowId, seq=$emissionCount, value=$value")
            }

            // Successful completion
            val endTime = System.nanoTime()
            val duration = endTime - startTime

            logger.debug("Flow collection completed: flowId=$flowId, emissions=$emissionCount, duration=${duration}ns")

            session.send(
                collectionCtx.flowCollectionCompleted(
                    collectorId = collectorId,
                    totalEmissions = emissionCount,
                    durationNanos = duration
                )
            )

        } catch (e: CancellationException) {
            // Collection cancelled
            logger.debug("Flow collection cancelled: flowId=$flowId, reason=${e.message}, emissions=$emissionCount")

            session.send(
                collectionCtx.flowCollectionCancelled(
                    collectorId = collectorId,
                    reason = e.message ?: "CancellationException",
                    emittedCount = emissionCount
                )
            )
            throw e

        } catch (e: Exception) {
            // Collection failed
            logger.debug("Flow collection failed: flowId=$flowId, reason=${e.message}, emissions=$emissionCount")

            session.send(
                collectionCtx.flowCollectionCancelled(
                    collectorId = collectorId,
                    reason = e.message ?: e.javaClass.simpleName,
                    emittedCount = emissionCount
                )
            )
            throw e
        }
    }

    // ========================================================================
    // Transform Operators
    // ========================================================================

    /**
     * Instrumented map operator - tracks each transformation.
     */
    fun <R> vizMap(transform: suspend (T) -> R): InstrumentedFlow<R> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "map"
        val newIndex = operatorIndex + 1
        
        // Emit operator applied event
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        
        val transformedFlow = delegate.map { value ->
            val result = transform(value)
            
            // Emit transformation event
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            session.send(
                FlowValueTransformed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = newFlowId,
                    operatorName = operatorName,
                    inputValuePreview = value?.toString()?.take(200) ?: "null",
                    outputValuePreview = result?.toString()?.take(200) ?: "null",
                    inputType = value?.javaClass?.simpleName ?: "null",
                    outputType = result?.javaClass?.simpleName ?: "null",
                    sequenceNumber = sequenceNumber++,
                    coroutineId = coroutineId
                )
            )
            
            result
        }
        
        return InstrumentedFlow(
            delegate = transformedFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.map" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented mapNotNull operator - tracks transformations that may filter.
     */
    fun <R : Any> vizMapNotNull(transform: suspend (T) -> R?): InstrumentedFlow<R> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "mapNotNull"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        
        val transformedFlow = delegate.mapNotNull { value ->
            val result = transform(value)
            
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            if (result != null) {
                session.send(
                    FlowValueTransformed(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        flowId = newFlowId,
                        operatorName = operatorName,
                        inputValuePreview = value?.toString()?.take(200) ?: "null",
                        outputValuePreview = result.toString().take(200),
                        inputType = value?.javaClass?.simpleName ?: "null",
                        outputType = result.javaClass.simpleName,
                        sequenceNumber = sequenceNumber++,
                        coroutineId = coroutineId
                    )
                )
            } else {
                session.send(
                    FlowValueFiltered(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        flowId = newFlowId,
                        operatorName = operatorName,
                        valuePreview = value?.toString()?.take(200) ?: "null",
                        valueType = value?.javaClass?.simpleName ?: "null",
                        passed = false,
                        sequenceNumber = sequenceNumber++,
                        coroutineId = coroutineId
                    )
                )
            }
            
            result
        }
        
        return InstrumentedFlow(
            delegate = transformedFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.mapNotNull" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented transform operator - for complex transformations.
     */
    fun <R> vizTransform(
        transform: suspend FlowCollector<R>.(value: T) -> Unit
    ): InstrumentedFlow<R> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "transform"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var inputSequence = 0
        var outputSequence = 0
        
        val transformedFlow = delegate.transform { inputValue ->
            val inputSeq = inputSequence++
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            // Wrap the collector to track outputs
            val wrappedCollector = object : FlowCollector<R> {
                override suspend fun emit(value: R) {
                    session.send(
                        FlowValueTransformed(
                            sessionId = session.sessionId,
                            seq = session.nextSeq(),
                            tsNanos = System.nanoTime(),
                            flowId = newFlowId,
                            operatorName = operatorName,
                            inputValuePreview = inputValue?.toString()?.take(200) ?: "null",
                            outputValuePreview = value?.toString()?.take(200) ?: "null",
                            inputType = inputValue?.javaClass?.simpleName ?: "null",
                            outputType = value?.javaClass?.simpleName ?: "null",
                            sequenceNumber = outputSequence++,
                            coroutineId = coroutineId
                        )
                    )
                    this@transform.emit(value)
                }
            }
            
            transform(wrappedCollector, inputValue)
        }
        
        return InstrumentedFlow(
            delegate = transformedFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.transform" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Filter Operators
    // ========================================================================

    /**
     * Instrumented filter operator - tracks which values pass.
     */
    fun vizFilter(predicate: suspend (T) -> Boolean): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "filter"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        
        val filteredFlow = delegate.filter { value ->
            val passed = predicate(value)
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            session.send(
                FlowValueFiltered(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = newFlowId,
                    operatorName = operatorName,
                    valuePreview = value?.toString()?.take(200) ?: "null",
                    valueType = value?.javaClass?.simpleName ?: "null",
                    passed = passed,
                    sequenceNumber = sequenceNumber++,
                    coroutineId = coroutineId
                )
            )
            
            passed
        }
        
        return InstrumentedFlow(
            delegate = filteredFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.filter" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented filterNot operator.
     */
    fun vizFilterNot(predicate: suspend (T) -> Boolean): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "filterNot"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        
        val filteredFlow = delegate.filterNot { value ->
            val matches = predicate(value)
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            session.send(
                FlowValueFiltered(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = newFlowId,
                    operatorName = operatorName,
                    valuePreview = value?.toString()?.take(200) ?: "null",
                    valueType = value?.javaClass?.simpleName ?: "null",
                    passed = !matches,  // Passes if predicate is false
                    sequenceNumber = sequenceNumber++,
                    coroutineId = coroutineId
                )
            )
            
            matches
        }
        
        return InstrumentedFlow(
            delegate = filteredFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.filterNot" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented filterIsInstance operator.
     * Note: Due to Kotlin inline restrictions, use the extension function version for reified types.
     */
    fun <R : Any> vizFilterIsInstance(clazz: Class<R>): InstrumentedFlow<R> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "filterIsInstance<${clazz.simpleName}>"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        
        val filteredFlow = delegate.transform { value ->
            val isInstance = clazz.isInstance(value)
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            session.send(
                FlowValueFiltered(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = newFlowId,
                    operatorName = operatorName,
                    valuePreview = value?.toString()?.take(200) ?: "null",
                    valueType = value?.javaClass?.simpleName ?: "null",
                    passed = isInstance,
                    sequenceNumber = sequenceNumber++,
                    coroutineId = coroutineId
                )
            )
            
            if (isInstance) {
                @Suppress("UNCHECKED_CAST")
                emit(value as R)
            }
        }
        
        return InstrumentedFlow(
            delegate = filteredFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.filterIsInstance" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented distinctUntilChanged operator.
     */
    fun vizDistinctUntilChanged(): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "distinctUntilChanged"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        var previousValue: Any? = UNSET
        
        val filteredFlow = delegate.transform { value ->
            val isDifferent = previousValue === UNSET || previousValue != value
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            session.send(
                FlowValueFiltered(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = newFlowId,
                    operatorName = operatorName,
                    valuePreview = value?.toString()?.take(200) ?: "null",
                    valueType = value?.javaClass?.simpleName ?: "null",
                    passed = isDifferent,
                    sequenceNumber = sequenceNumber++,
                    coroutineId = coroutineId
                )
            )
            
            if (isDifferent) {
                previousValue = value
                emit(value)
            }
        }
        
        return InstrumentedFlow(
            delegate = filteredFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.distinctUntilChanged" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Take/Drop Operators
    // ========================================================================

    /**
     * Instrumented take operator.
     */
    fun vizTake(count: Int): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "take($count)"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.take(count),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.take($count)" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented takeWhile operator.
     */
    fun vizTakeWhile(predicate: suspend (T) -> Boolean): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "takeWhile"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        
        val takenFlow = delegate.takeWhile { value ->
            val take = predicate(value)
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            session.send(
                FlowValueFiltered(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = newFlowId,
                    operatorName = operatorName,
                    valuePreview = value?.toString()?.take(200) ?: "null",
                    valueType = value?.javaClass?.simpleName ?: "null",
                    passed = take,
                    sequenceNumber = sequenceNumber++,
                    coroutineId = coroutineId
                )
            )
            
            take
        }
        
        return InstrumentedFlow(
            delegate = takenFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.takeWhile" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented drop operator.
     */
    fun vizDrop(count: Int): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "drop($count)"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.drop(count),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.drop($count)" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented dropWhile operator.
     */
    fun vizDropWhile(predicate: suspend (T) -> Boolean): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "dropWhile"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.dropWhile(predicate),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.dropWhile" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Side Effect Operators
    // ========================================================================

    /**
     * Instrumented onEach operator - tracks side effects.
     */
    fun vizOnEach(action: suspend (T) -> Unit): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "onEach"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        var sequenceNumber = 0
        
        val sideEffectFlow = delegate.onEach { value ->
            val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId
            
            session.send(
                FlowValueTransformed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    flowId = newFlowId,
                    operatorName = operatorName,
                    inputValuePreview = value?.toString()?.take(200) ?: "null",
                    outputValuePreview = value?.toString()?.take(200) ?: "null",
                    inputType = value?.javaClass?.simpleName ?: "null",
                    outputType = value?.javaClass?.simpleName ?: "null",
                    sequenceNumber = sequenceNumber++,
                    coroutineId = coroutineId
                )
            )
            
            action(value)
        }
        
        return InstrumentedFlow(
            delegate = sideEffectFlow,
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.onEach" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented onStart operator.
     */
    fun vizOnStart(action: suspend FlowCollector<T>.() -> Unit): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "onStart"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.onStart(action),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.onStart" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented onCompletion operator.
     */
    fun vizOnCompletion(action: suspend FlowCollector<T>.(cause: Throwable?) -> Unit): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "onCompletion"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.onCompletion(action),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.onCompletion" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Buffer Operators (Backpressure)
    // ========================================================================

    /**
     * Instrumented buffer operator - tracks backpressure behavior.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun vizBuffer(
        capacity: Int = 64,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
    ): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "buffer($capacity, $onBufferOverflow)"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.buffer(capacity, onBufferOverflow),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.buffer($capacity)" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented conflate operator - drops intermediate values.
     */
    fun vizConflate(): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "conflate"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.conflate(),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.conflate" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Timing Operators
    // ========================================================================

    /**
     * Instrumented debounce operator.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun vizDebounce(timeoutMillis: Long): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "debounce(${timeoutMillis}ms)"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.debounce(timeoutMillis),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.debounce(${timeoutMillis}ms)" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented sample operator.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun vizSample(periodMillis: Long): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "sample(${periodMillis}ms)"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.sample(periodMillis),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.sample(${periodMillis}ms)" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Error Handling Operators
    // ========================================================================

    /**
     * Instrumented catch operator.
     */
    fun vizCatch(action: suspend FlowCollector<T>.(cause: Throwable) -> Unit): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "catch"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.catch(action),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.catch" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented retry operator.
     */
    fun vizRetry(
        retries: Long = Long.MAX_VALUE,
        predicate: suspend (cause: Throwable) -> Boolean = { true }
    ): InstrumentedFlow<T> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = if (retries == Long.MAX_VALUE) "retry" else "retry($retries)"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.retry(retries, predicate),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.$operatorName" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Flattening Operators
    // ========================================================================

    /**
     * Instrumented flatMapConcat operator.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <R> vizFlatMapConcat(transform: suspend (T) -> Flow<R>): InstrumentedFlow<R> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "flatMapConcat"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.flatMapConcat(transform),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.flatMapConcat" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented flatMapMerge operator.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <R> vizFlatMapMerge(
        concurrency: Int = DEFAULT_CONCURRENCY,
        transform: suspend (T) -> Flow<R>
    ): InstrumentedFlow<R> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "flatMapMerge($concurrency)"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.flatMapMerge(concurrency, transform),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.flatMapMerge($concurrency)" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    /**
     * Instrumented flatMapLatest operator.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <R> vizFlatMapLatest(transform: suspend (T) -> Flow<R>): InstrumentedFlow<R> {
        val newFlowId = "flow-${session.nextSeq()}"
        val operatorName = "flatMapLatest"
        val newIndex = operatorIndex + 1
        
        session.send(ctx.forOperator(newFlowId, operatorName, newIndex).flowOperatorApplied(operatorName))
        
        return InstrumentedFlow(
            delegate = delegate.flatMapLatest(transform),
            session = session,
            flowId = newFlowId,
            flowType = flowType,
            label = label?.let { "$it.flatMapLatest" },
            sourceFlowId = flowId,
            operatorIndex = newIndex
        )
    }

    // ========================================================================
    // Async Collection Operations
    // ========================================================================

    /**
     * Launch collection of this flow in the given scope.
     * 
     * This is the equivalent of `launchIn` but with visualization tracking.
     * The collection runs in a new coroutine and emits events for:
     * - Coroutine launch
     * - Collection start/complete/cancel
     * - All value emissions
     * 
     * @param scope The CoroutineScope to launch in
     * @param onEach Optional action for each emitted value
     * @return Job handle for the collection coroutine
     */
    fun vizLaunchIn(
        scope: CoroutineScope,
        onEach: (suspend (T) -> Unit)? = null
    ): Job {
        val coroutineId = "flow-collector-${session.nextSeq()}"
        
        session.send(
            FlowOperatorApplied(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                flowId = flowId,
                sourceFlowId = sourceFlowId ?: flowId,
                operatorName = "launchIn",
                operatorIndex = operatorIndex + 1,
                label = label,
                coroutineId = coroutineId
            )
        )
        
        return scope.launch {
            if (onEach != null) {
                collect { value -> onEach(value) }
            } else {
                collect { }
            }
        }
    }

    /**
     * Convert this cold flow to a hot SharedFlow.
     * 
     * @param scope The CoroutineScope for the sharing coroutine
     * @param started The sharing start strategy
     * @param replay Number of values to replay to new subscribers
     * @return An InstrumentedSharedFlow
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun vizShareIn(
        scope: CoroutineScope,
        started: SharingStarted,
        replay: Int = 0
    ): SharedFlow<T> {
        val sharedFlowId = "sharedflow-from-${flowId}-${session.nextSeq()}"
        
        session.send(
            FlowOperatorApplied(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                flowId = sharedFlowId,
                sourceFlowId = flowId,
                operatorName = "shareIn(replay=$replay)",
                operatorIndex = operatorIndex + 1,
                label = label,
                coroutineId = null
            )
        )
        
        // Create the shared flow
        val sharedFlow = delegate.shareIn(scope, started, replay)
        
        // Emit FlowCreated for the new SharedFlow
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = "unknown",
                flowId = sharedFlowId,
                flowType = "SharedFlow",
                label = label?.let { "$it.shareIn" },
                scopeId = null
            )
        )
        
        return sharedFlow
    }

    /**
     * Convert this cold flow to a hot StateFlow.
     * 
     * @param scope The CoroutineScope for the state coroutine
     * @param started The sharing start strategy
     * @param initialValue The initial value of the StateFlow
     * @return A StateFlow
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun vizStateIn(
        scope: CoroutineScope,
        started: SharingStarted,
        initialValue: T
    ): StateFlow<T> {
        val stateFlowId = "stateflow-from-${flowId}-${session.nextSeq()}"
        
        session.send(
            FlowOperatorApplied(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                flowId = stateFlowId,
                sourceFlowId = flowId,
                operatorName = "stateIn(initial=$initialValue)",
                operatorIndex = operatorIndex + 1,
                label = label,
                coroutineId = null
            )
        )
        
        // Create the state flow
        val stateFlow = delegate.stateIn(scope, started, initialValue)
        
        // Emit FlowCreated for the new StateFlow
        session.send(
            FlowCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = "unknown",
                flowId = stateFlowId,
                flowType = "StateFlow",
                label = label?.let { "$it.stateIn" },
                scopeId = null
            )
        )
        
        return stateFlow
    }

    // ========================================================================
    // Terminal Operators with Tracking
    // ========================================================================

    /**
     * Collect the first value and return it.
     */
    suspend fun vizFirst(): T {
        val collectorId = "first-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        val result = delegate.first()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, 0, result))
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, 1, 0))
        
        return result
    }

    /**
     * Collect the first value matching the predicate.
     */
    suspend fun vizFirst(predicate: suspend (T) -> Boolean): T {
        val collectorId = "first-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        val result = delegate.first(predicate)
        
        session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, 0, result))
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, 1, 0))
        
        return result
    }

    /**
     * Collect the first value or null if empty.
     */
    suspend fun vizFirstOrNull(): T? {
        val collectorId = "firstOrNull-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        val result = delegate.firstOrNull()
        
        if (result != null) {
            session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, 0, result))
            session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, 1, 0))
        } else {
            session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, 0, 0))
        }
        
        return result
    }

    /**
     * Collect the last value.
     */
    suspend fun vizLast(): T {
        val collectorId = "last-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        var count = 0
        val result = delegate.onEach { count++ }.last()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, count - 1, result))
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, count, System.nanoTime() - startTime))
        
        return result
    }

    /**
     * Collect all values into a list.
     */
    suspend fun vizToList(): List<T> {
        val collectorId = "toList-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        var count = 0
        val result = delegate.onEach { 
            session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, count++, it))
        }.toList()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, count, System.nanoTime() - startTime))
        
        return result
    }

    /**
     * Collect all values into a set.
     */
    suspend fun vizToSet(): Set<T> {
        val collectorId = "toSet-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        var count = 0
        val result = delegate.onEach { 
            session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, count++, it))
        }.toSet()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, count, System.nanoTime() - startTime))
        
        return result
    }

    /**
     * Count the number of elements.
     */
    suspend fun vizCount(): Int {
        val collectorId = "count-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        val result = delegate.count()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, result, System.nanoTime() - startTime))
        
        return result
    }

    /**
     * Reduce values using the given operation.
     */
    suspend fun vizReduce(operation: suspend (accumulator: T, value: T) -> T): T {
        val collectorId = "reduce-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        var count = 0
        val result = delegate.onEach { count++ }.reduce(operation)
        
        session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, count - 1, result))
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, count, System.nanoTime() - startTime))
        
        return result
    }

    /**
     * Fold values using the given operation.
     */
    suspend fun <R> vizFold(initial: R, operation: suspend (accumulator: R, value: T) -> R): R {
        val collectorId = "fold-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionStarted(collectorId))
        
        var count = 0
        val result = delegate.onEach { count++ }.fold(initial, operation)
        
        session.send(ctx.copy(coroutineId = coroutineId).flowValueEmitted(collectorId, count, result))
        session.send(ctx.copy(coroutineId = coroutineId).flowCollectionCompleted(collectorId, count, System.nanoTime() - startTime))
        
        return result
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstrumentedFlow::class.java)
        private val UNSET = Any()
        private const val DEFAULT_CONCURRENCY = 16
    }
}

// ============================================================================
// Extension Functions
// ============================================================================

/**
 * Extension function to wrap any Flow with instrumentation.
 */
fun <T> Flow<T>.instrumented(
    session: VizSession,
    flowId: String,
    flowType: String = "Cold",
    label: String? = null
): InstrumentedFlow<T> {
    // Emit FlowCreated when wrapping
    session.send(
        FlowCreated(
            sessionId = session.sessionId,
            seq = session.nextSeq(),
            tsNanos = System.nanoTime(),
            coroutineId = "unknown",
            flowId = flowId,
            flowType = flowType,
            label = label,
            scopeId = null
        )
    )
    
    return InstrumentedFlow(this, session, flowId, flowType, label)
}

/**
 * Extension function to wrap any Flow with auto-generated ID.
 */
fun <T> Flow<T>.vizInstrumented(
    session: VizSession,
    flowType: String = "Cold",
    label: String? = null
): InstrumentedFlow<T> {
    val flowId = "flow-${session.nextSeq()}"
    return this.instrumented(session, flowId, flowType, label)
}
