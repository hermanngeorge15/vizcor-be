package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.InstrumentedDispatcher
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Container for instrumented dispatchers used in visualization scenarios.
 * 
 * This class creates and holds instrumented versions of common dispatchers
 * that automatically emit DispatcherSelected and ThreadAssigned events.
 * 
 * Usage:
 * ```
 * val dispatchers = VizDispatchers(session)
 * 
 * // Use in VizScope via context
 * val scope = VizScope(session, context = dispatchers.default)
 * 
 * // Or pass to individual launches
 * scope.vizLaunch(context = dispatchers.io) {
 *     // Runs on IO dispatcher with tracking
 * }
 * ```
 */
class VizDispatchers(
    private val session: VizSession,
    private val scopeId: String = "dispatchers"
) {
    
    /**
     * Instrumented version of Dispatchers.Default.
     * Use for CPU-intensive operations.
     */
    val default: InstrumentedDispatcher = InstrumentedDispatcher(
        delegate = Dispatchers.Default,
        session = session,
        dispatcherId = "dispatcher-default-$scopeId",
        dispatcherName = "Dispatchers.Default"
    )
    
    /**
     * Instrumented version of Dispatchers.IO.
     * Use for I/O operations (file, network, database).
     */
    val io: InstrumentedDispatcher = InstrumentedDispatcher(
        delegate = Dispatchers.IO,
        session = session,
        dispatcherId = "dispatcher-io-$scopeId",
        dispatcherName = "Dispatchers.IO"
    )
    
    /**
     * Instrumented version of Dispatchers.Unconfined.
     * Use with caution - coroutine resumes in whatever thread the suspending function uses.
     */
    val unconfined: InstrumentedDispatcher = InstrumentedDispatcher(
        delegate = Dispatchers.Unconfined,
        session = session,
        dispatcherId = "dispatcher-unconfined-$scopeId",
        dispatcherName = "Dispatchers.Unconfined"
    )
    
    /**
     * Wrap any custom dispatcher with instrumentation.
     * 
     * @param dispatcher The dispatcher to wrap
     * @param name Human-readable name for the dispatcher (shown in events)
     * @return Instrumented dispatcher that emits tracking events
     * 
     * @example
     * ```
     * val myThreadPool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
     * val instrumented = dispatchers.instrument(myThreadPool, "MyThreadPool")
     * 
     * scope.vizLaunch(context = instrumented) {
     *     // Tracked on your custom dispatcher
     * }
     * ```
     */
    fun instrument(
        dispatcher: CoroutineDispatcher,
        name: String? = null
    ): InstrumentedDispatcher {
        // If already instrumented, return as-is
        if (dispatcher is InstrumentedDispatcher) {
            return dispatcher
        }
        
        // Wrap with instrumentation
        return InstrumentedDispatcher(
            delegate = dispatcher,
            session = session,
            dispatcherId = "dispatcher-custom-${dispatcher.hashCode()}-$scopeId",
            dispatcherName = name ?: dispatcher.toString()
        )
    }
    
    companion object {
        /**
         * Create VizDispatchers for a session with a specific scope ID.
         * Useful when you want multiple dispatcher sets in the same session.
         */
        fun create(session: VizSession, scopeId: String = "dispatchers"): VizDispatchers {
            return VizDispatchers(session, scopeId)
        }
    }
}

