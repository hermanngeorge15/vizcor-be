package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.dispatcher.DispatcherSelected
import com.jh.proj.coroutineviz.events.dispatcher.ThreadAssigned
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * CoroutineDispatcher wrapper that emits tracking events for visualization.
 *
 * InstrumentedDispatcher wraps a real [CoroutineDispatcher] and emits events
 * when coroutines are dispatched and assigned to threads. This enables
 * visualization of dispatcher behavior and thread assignment patterns.
 *
 * Events emitted:
 * - [DispatcherSelected] when dispatch() is called (dispatcher chosen)
 * - [ThreadAssigned] when the coroutine actually starts running on a thread
 *
 * Usage:
 * ```kotlin
 * val instrumentedDefault = InstrumentedDispatcher(
 *     delegate = Dispatchers.Default,
 *     session = session,
 *     dispatcherId = "default",
 *     dispatcherName = "Dispatchers.Default"
 * )
 *
 * // Use in coroutine context
 * scope.vizLaunch(context = instrumentedDefault) {
 *     // Runs with dispatcher tracking
 * }
 * ```
 *
 * @property delegate The underlying dispatcher to delegate execution to
 * @property session The visualization session for emitting events
 * @property dispatcherId Unique identifier for this dispatcher instance
 * @property dispatcherName Human-readable name for display in events
 *
 * @see VizDispatchers For convenient access to pre-configured instrumented dispatchers
 */
class InstrumentedDispatcher(
    private val delegate: CoroutineDispatcher,
    private val session: VizSession,
    val dispatcherId: String,
    val dispatcherName: String
) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // Get coroutine info from context
        val vizElement = context[VizCoroutineElement]

        // Note: dispatch() is not a suspend function, so we use send() (non-suspend)
        if (vizElement != null) {
            // Emit DispatcherSelected event
            session.send(
                DispatcherSelected(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    coroutineId = vizElement.coroutineId,
                    jobId = vizElement.jobId,
                    parentCoroutineId = vizElement.parentCoroutineId,
                    scopeId = vizElement.scopeId,
                    label = vizElement.label,
                    dispatcherId = dispatcherId,
                    dispatcherName = dispatcherName
                )
            )
        }

        // Wrap the Runnable to capture thread assignment
        val instrumentedBlock = Runnable {
            if (vizElement != null) {
                // Emit ThreadAssigned when actually running on thread
                session.send(
                    ThreadAssigned(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        coroutineId = vizElement.coroutineId,
                        jobId = vizElement.jobId,
                        parentCoroutineId = vizElement.parentCoroutineId,
                        scopeId = vizElement.scopeId,
                        label = vizElement.label,
                        threadId = Thread.currentThread().threadId(),
                        threadName = Thread.currentThread().name,
                        dispatcherName = dispatcherName
                    )
                )
            }

            // Execute the actual block
            block.run()
        }

        // Dispatch to real dispatcher
        delegate.dispatch(context, instrumentedBlock)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return delegate.isDispatchNeeded(context)
    }
}
