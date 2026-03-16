package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.*

/**
 * FlowEventContext carries common Flow metadata for event creation.
 * 
 * This provides extension functions that automatically fill in common fields
 * when emitting Flow-related events, similar to EventContext for coroutines.
 * 
 * Usage:
 * ```kotlin
 * val ctx = FlowEventContext(session, flowId, "Cold", "my-flow", coroutineId)
 * session.send(ctx.flowCreated())
 * session.send(ctx.flowValueEmitted(collectorId, 0, value))
 * ```
 */
data class FlowEventContext(
    val session: VizSession,
    val flowId: String,
    val flowType: String,  // "Cold", "Hot", "StateFlow", "SharedFlow"
    val label: String? = null,
    val coroutineId: String? = null,
    val scopeId: String? = null,
    val sourceFlowId: String? = null,  // For operator chains
    val operatorIndex: Int = 0
) {
    val sessionId: String get() = session.sessionId
    
    fun nextSeq(): Long = session.nextSeq()
    fun timestamp(): Long = System.nanoTime()
    
    /**
     * Create a child context for an operator in the chain.
     */
    fun forOperator(
        newFlowId: String,
        operatorName: String,
        newOperatorIndex: Int
    ): FlowEventContext = copy(
        flowId = newFlowId,
        sourceFlowId = this.flowId,
        operatorIndex = newOperatorIndex
    )
}

// ============================================================================
// Flow Lifecycle Events
// ============================================================================

fun FlowEventContext.flowCreated(): FlowCreated = FlowCreated(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId ?: "unknown",
    flowId = flowId,
    flowType = flowType,
    label = label,
    scopeId = scopeId
)

fun FlowEventContext.flowCollectionStarted(
    collectorId: String
): FlowCollectionStarted = FlowCollectionStarted(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId ?: "unknown",
    flowId = flowId,
    collectorId = collectorId,
    label = label
)

fun FlowEventContext.flowValueEmitted(
    collectorId: String,
    sequenceNumber: Int,
    value: Any?
): FlowValueEmitted = FlowValueEmitted(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId ?: "unknown",
    flowId = flowId,
    collectorId = collectorId,
    sequenceNumber = sequenceNumber,
    valuePreview = value?.toString()?.take(200) ?: "null",
    valueType = value?.javaClass?.simpleName ?: "null"
)

fun FlowEventContext.flowCollectionCompleted(
    collectorId: String,
    totalEmissions: Int,
    durationNanos: Long
): FlowCollectionCompleted = FlowCollectionCompleted(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId ?: "unknown",
    flowId = flowId,
    collectorId = collectorId,
    totalEmissions = totalEmissions,
    durationNanos = durationNanos
)

fun FlowEventContext.flowCollectionCancelled(
    collectorId: String,
    reason: String?,
    emittedCount: Int
): FlowCollectionCancelled = FlowCollectionCancelled(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId ?: "unknown",
    flowId = flowId,
    collectorId = collectorId,
    reason = reason,
    emittedCount = emittedCount
)

fun FlowEventContext.flowBufferOverflow(
    droppedValue: Any?,
    bufferSize: Int,
    overflowStrategy: String
): FlowBufferOverflow = FlowBufferOverflow(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    coroutineId = coroutineId ?: "unknown",
    flowId = flowId,
    droppedValue = droppedValue?.toString()?.take(100),
    bufferSize = bufferSize,
    overflowStrategy = overflowStrategy
)

// ============================================================================
// Flow Operator Events
// ============================================================================

fun FlowEventContext.flowOperatorApplied(
    operatorName: String
): FlowOperatorApplied = FlowOperatorApplied(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    flowId = flowId,
    sourceFlowId = sourceFlowId ?: flowId,
    operatorName = operatorName,
    operatorIndex = operatorIndex,
    label = label,
    coroutineId = coroutineId
)

fun FlowEventContext.flowValueTransformed(
    operatorName: String,
    inputValue: Any?,
    outputValue: Any?,
    sequenceNumber: Int,
    collectorId: String? = null
): FlowValueTransformed = FlowValueTransformed(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    flowId = flowId,
    operatorName = operatorName,
    inputValuePreview = inputValue?.toString()?.take(200) ?: "null",
    outputValuePreview = outputValue?.toString()?.take(200) ?: "null",
    inputType = inputValue?.javaClass?.simpleName ?: "null",
    outputType = outputValue?.javaClass?.simpleName ?: "null",
    sequenceNumber = sequenceNumber,
    coroutineId = coroutineId,
    collectorId = collectorId
)

fun FlowEventContext.flowValueFiltered(
    operatorName: String,
    value: Any?,
    passed: Boolean,
    sequenceNumber: Int,
    collectorId: String? = null
): FlowValueFiltered = FlowValueFiltered(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    flowId = flowId,
    operatorName = operatorName,
    valuePreview = value?.toString()?.take(200) ?: "null",
    valueType = value?.javaClass?.simpleName ?: "null",
    passed = passed,
    sequenceNumber = sequenceNumber,
    coroutineId = coroutineId,
    collectorId = collectorId
)

fun FlowEventContext.flowBackpressure(
    collectorId: String,
    reason: String,
    pendingEmissions: Int,
    bufferCapacity: Int? = null,
    durationNanos: Long? = null
): FlowBackpressure = FlowBackpressure(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    flowId = flowId,
    collectorId = collectorId,
    reason = reason,
    pendingEmissions = pendingEmissions,
    bufferCapacity = bufferCapacity,
    durationNanos = durationNanos,
    coroutineId = coroutineId
)

// ============================================================================
// StateFlow Events
// ============================================================================

fun FlowEventContext.stateFlowValueChanged(
    oldValue: Any?,
    newValue: Any?,
    subscriberCount: Int
): StateFlowValueChanged = StateFlowValueChanged(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    flowId = flowId,
    oldValuePreview = oldValue?.toString()?.take(200) ?: "null",
    newValuePreview = newValue?.toString()?.take(200) ?: "null",
    valueType = newValue?.javaClass?.simpleName ?: "null",
    subscriberCount = subscriberCount,
    coroutineId = coroutineId,
    label = label
)

// ============================================================================
// SharedFlow Events
// ============================================================================

fun FlowEventContext.sharedFlowEmission(
    value: Any?,
    subscriberCount: Int,
    replayCache: Int,
    extraBufferCapacity: Int
): SharedFlowEmission = SharedFlowEmission(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    flowId = flowId,
    valuePreview = value?.toString()?.take(200) ?: "null",
    valueType = value?.javaClass?.simpleName ?: "null",
    subscriberCount = subscriberCount,
    replayCache = replayCache,
    extraBufferCapacity = extraBufferCapacity,
    coroutineId = coroutineId,
    label = label
)

fun FlowEventContext.sharedFlowSubscription(
    collectorId: String,
    action: String,  // "subscribed" or "unsubscribed"
    subscriberCount: Int
): SharedFlowSubscription = SharedFlowSubscription(
    sessionId = sessionId,
    seq = nextSeq(),
    tsNanos = timestamp(),
    flowId = flowId,
    collectorId = collectorId,
    action = action,
    subscriberCount = subscriberCount,
    coroutineId = coroutineId,
    label = label
)

