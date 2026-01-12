package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.flow.FlowCollectionCancelled
import com.jh.proj.coroutineviz.events.flow.FlowCollectionCompleted
import com.jh.proj.coroutineviz.events.flow.FlowCollectionStarted
import com.jh.proj.coroutineviz.events.flow.FlowValueEmitted
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.slf4j.LoggerFactory

/**
 * Flow wrapper that emits collection start/finish/cancel events and each value emission
 * without altering the upstream Flow logic.
 */
class InstrumentedFlow<T>(
    private val delegate: Flow<T>,
    private val session: VizSession,
    private val flowId: String,
    private val flowType: String = "Cold",
    private val label: String? = null,
) : Flow<T>{
    override suspend fun collect(collector: FlowCollector<T>) {
        val collectorId = "collector-${session.nextSeq()}"
        val coroutineContext = currentCoroutineContext()
        val coroutineId = coroutineContext[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()

        logger.debug("Flow collection started: flowId=$flowId, collectorId=$collectorId, coroutineId=$coroutineId")

        // Emit collection started event
        session.eventBus.send(
            FlowCollectionStarted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                coroutineId = coroutineId,
                flowId = flowId,
                collectorId = collectorId,
                label = label
            )
        )

        var emissionCount = 0

        try {
            // Wrap the collector to intercept emissions
            delegate.collect { value ->
                val emitTime = System.nanoTime()

                // Emit value event BEFORE passing to collector
                session.eventBus.send(
                    FlowValueEmitted(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = emitTime,
                        coroutineId = coroutineId,
                        flowId = flowId,
                        collectorId = collectorId,
                        sequenceNumber = emissionCount,
                        valuePreview = value.toString().take(200),
                        valueType = value?.javaClass?.simpleName ?: "null"
                    )
                )

                emissionCount++

                // Pass value to the actual collector
                collector.emit(value)

                logger.trace("Flow value emitted: flowId=$flowId, seq=$emissionCount, value=$value")
            }

            // Successful completion
            val endTime = System.nanoTime()
            val duration = endTime - startTime

            logger.debug("Flow collection completed: flowId=$flowId, emissions=$emissionCount, duration=${duration}ns")

            session.eventBus.send(
                FlowCollectionCompleted(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = endTime,
                    coroutineId = coroutineId,
                    flowId = flowId,
                    collectorId = collectorId,
                    totalEmissions = emissionCount,
                    durationNanos = duration
                )
            )

        } catch (e: Exception) {
            // Collection cancelled or failed
            val endTime = System.nanoTime()

            logger.debug("Flow collection cancelled: flowId=$flowId, reason=${e.message}, emissions=$emissionCount")

            session.eventBus.send(
                FlowCollectionCancelled(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = endTime,
                    coroutineId = coroutineId,
                    flowId = flowId,
                    collectorId = collectorId,
                    reason = e.message ?: e.javaClass.simpleName,
                    emittedCount = emissionCount
                )
            )

            throw e
        }
    }


    companion object{
        private val logger = LoggerFactory.getLogger(InstrumentedFlow::class.java)
    }
}

/**
 * Extension function to wrap any Flow with instrumentation
 */
fun <T> Flow<T>.instrumented(
    session: VizSession,
    flowId: String,
    flowType: String = "Cold",
    label: String? = null
): Flow<T> = InstrumentedFlow(this, session, flowId, flowType, label)

/**
 * Extension function to wrap any Flow with auto-generated ID
 */
fun <T> Flow<T>.vizInstrumented(
    session: VizSession,
    flowType: String = "Cold",
    label: String? = null
): Flow<T> {
    val flowId = "flow-${session.nextSeq()}"
    return InstrumentedFlow(this, session, flowId, flowType, label)
}
