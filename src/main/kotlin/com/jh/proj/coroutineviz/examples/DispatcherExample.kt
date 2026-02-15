package com.jh.proj.coroutineviz.examples

import com.jh.proj.coroutineviz.events.dispatcher.DispatcherSelected
import com.jh.proj.coroutineviz.events.dispatcher.ThreadAssigned
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizDispatchers
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.coroutineScope

/**
 * Example showing how to use VizDispatchers in scenarios.
 * 
 * This demonstrates:
 * 1. Creating VizDispatchers for a session
 * 2. Using dispatchers via context in VizScope
 * 3. Mixing different dispatchers in the same scenario
 * 4. Verifying dispatcher tracking works
 */
suspend fun dispatcherExampleScenario() = coroutineScope {
    println("\n" + "=".repeat(60))
    println("Dispatcher Example Scenario")
    println("=".repeat(60))
    
    // Create session
    val session = VizSession("dispatcher-example")
    
    // Create instrumented dispatchers for this session
    val dispatchers = VizDispatchers(session, scopeId = "example")
    
    println("\n📦 Created VizDispatchers:")
    println("   - default: ${dispatchers.default.dispatcherName}")
    println("   - io: ${dispatchers.io.dispatcherName}")
    println("   - unconfined: ${dispatchers.unconfined.dispatcherName}")
    
    // ========================================
    // Example 1: Use Default dispatcher
    // ========================================
    println("\n🟢 Example 1: Default Dispatcher")
    
    val scope1 = VizScope(session, context = dispatchers.default)
    scope1.vizLaunch(label = "cpu-work") {
        println("   [cpu-work] Running on: ${Thread.currentThread().name}")
        vizDelay(100)
        println("   [cpu-work] Completed")
    }.join()
    
    // ========================================
    // Example 2: Use IO dispatcher
    // ========================================
    println("\n🔵 Example 2: IO Dispatcher")
    
    val scope2 = VizScope(session, context = dispatchers.io)
    scope2.vizLaunch(label = "file-reader") {
        println("   [file-reader] Running on: ${Thread.currentThread().name}")
        vizDelay(100)
        println("   [file-reader] Simulating file read")
        vizDelay(100)
        println("   [file-reader] Completed")
    }.join()
    
    // ========================================
    // Example 3: Mix dispatchers in same scope
    // ========================================
    println("\n🟡 Example 3: Mixed Dispatchers")
    
    val scope3 = VizScope(session, context = dispatchers.default)
    scope3.vizLaunch(label = "parent") {
        println("   [parent] Running on: ${Thread.currentThread().name}")
        
        // Child 1: Stays on Default
        vizLaunch(label = "child-default") {
            println("   [child-default] Running on: ${Thread.currentThread().name}")
            vizDelay(100)
        }
        
        // Child 2: Switches to IO
        vizLaunch(label = "child-io", context = dispatchers.io) {
            println("   [child-io] Running on: ${Thread.currentThread().name}")
            vizDelay(100)
        }
        
        println("   [parent] Waiting for children...")
    }.join()
    
    // ========================================
    // Verify Events
    // ========================================
    println("\n📊 Event Summary:")
    val events = session.store.all()
    val dispatcherEvents = events.filterIsInstance<DispatcherSelected>()
    val threadEvents = events.filterIsInstance<ThreadAssigned>()
    
    println("   Total events: ${events.size}")
    println("   DispatcherSelected events: ${dispatcherEvents.size}")
    println("   ThreadAssigned events: ${threadEvents.size}")
    
    if (dispatcherEvents.isEmpty()) {
        println("\n⚠️  WARNING: No DispatcherSelected events found!")
        println("   Dispatcher tracking may not be working.")
    } else {
        println("\n✅ SUCCESS: Dispatcher tracking is working!")
        println("\nDispatchers used:")
        dispatcherEvents.forEach { event ->
            println("   - ${event.dispatcherName} (${event.dispatcherId})")
        }
    }
    
    println("\n" + "=".repeat(60))
    println("Example Complete!")
    println("=".repeat(60) + "\n")
}

