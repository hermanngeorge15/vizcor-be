package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.events.CoroutineCompleted
import com.jh.proj.coroutineviz.events.CoroutineCreated
import com.jh.proj.coroutineviz.events.CoroutineStarted
import com.jh.proj.coroutineviz.wrappers.VizDispatchers
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class VizEventMain {
    suspend fun main() = coroutineScope {
        val session = VizSession(sessionId = "session-1")

        // 1) Start a "live logger" that listens on the EventBus
        val liveLogger = launch {
            session.bus.stream().collect { e ->
                println("LIVE EVENT: $e")
            }
        }

        // 2) Manually create a fake coroutine lifecycle
        val cid = "coro-1"
        val jid = "job-1"
        val parent: String? = null
        val scopeId = "scope-1"
        val label = "root"

        fun nowSeq() = session.nextSeq()
        fun nowTs() = System.nanoTime()

        session.sent(
            CoroutineCreated(
                sessionId = session.sessionId,
                seq = nowSeq(),
                tsNanos = nowTs(),
                coroutineId = cid,
                jobId = jid,
                parentCoroutineId = parent,
                scopeId = scopeId,
                label = label
            )
        )
        session.sent(
            CoroutineStarted(
                sessionId = session.sessionId,
                seq = nowSeq(),
                tsNanos = nowTs(),
                coroutineId = cid,
                jobId = jid,
                parentCoroutineId = parent,
                scopeId = scopeId,
                label = label
            )
        )
        session.sent(
            CoroutineCompleted(
                sessionId = session.sessionId,
                seq = nowSeq(),
                tsNanos = nowTs(),
                coroutineId = cid,
                jobId = jid,
                parentCoroutineId = parent,
                scopeId = scopeId,
                label = label
            )
        )

        // small delay to let logger process
        delay(100)

        // 3) Inspect snapshot
        println("\nSNAPSHOT:")
        session.snapshot.coroutines.values.forEach { node ->
            println(node)
        }

        liveLogger.cancel()
    }

    suspend fun main2() = coroutineScope {
        val session = VizSession("session-A")

        // Subscribe to live events
        val live = launch {
            session.bus.stream().collect { logger.info("LIVE: $it") }
        }

        // Create an independent VizScope
        // It runs on its own coroutine scope (Dispatchers.Default by default)
        // You can pass a custom context: VizScope(session, Dispatchers.IO, "custom-scope")
        val viz = VizScope(session)

        // Launch the visualization hierarchy - runs independently of main2's scope
        viz.vizLaunch("parent")   {
            vizLaunch("child-1") {
                vizLaunch("child-1-1") {
                    println("vizLaunch1")
                    vizDelay(2000)
                }
                println("vizLaunch11")
                vizDelay(2000)
            }
            vizLaunch("child-2") {
                vizLaunch("child-2-1") {
                    println("vizLaunch2")
                    vizDelay(1500)
                }
                println("vizLaunch21")
                vizDelay(1500)
            }
        }

        // Wait for the scenario to complete
//        job.join()

        // Give time for all events to propagate
//        delay(3000)
        delay(5000)
        logger.info("=== SNAPSHOT ===")
        session.snapshot.coroutines.values.forEach { logger.info(it.toString()) }

        // Clean up
        live.cancel()
    }

    /**
     * Test 3: Basic vizAsync functionality
     * Tests async/await pattern with proper event tracking
     */
    suspend fun testAsyncBasic() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("TEST 3: Basic vizAsync - Async/Await Pattern")
        logger.info("=".repeat(60))
        
        val session = VizSession("test-async-basic")
        
        // Live event logger
        val live = launch {
            session.bus.stream().collect { event ->
                logger.info("📡 EVENT: ${event.kind} | ${event.seq} | $event")
            }
        }
        
        val viz = VizScope(session)
        
        // Launch parent that creates async tasks
        val job = viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Starting async tasks...")
            
            // Create multiple async tasks
            val deferred1 = vizAsync("compute-A") {
                logger.info("⚙️ Async A: Computing...")
                vizDelay(100)
                logger.info("✅ Async A: Done!")
                "Result-A"
            }
            
            val deferred2 = vizAsync("compute-B") {
                logger.info("⚙️ Async B: Computing...")
                vizDelay(200)
                logger.info("✅ Async B: Done!")
                "Result-B"
            }
            
            // Await results (will track suspension)
            logger.info("⏳ Parent: Awaiting result A...")
            val resultA = deferred1.await()
            logger.info("✅ Parent: Got $resultA")
            
            logger.info("⏳ Parent: Awaiting result B...")
            val resultB = deferred2.await()
            logger.info("✅ Parent: Got $resultB")
            
            logger.info("🎉 Parent: All async tasks completed! Results: $resultA, $resultB")
        }
        
        // Wait for completion
        delay(1000)
        
        logger.info("\n" + "=".repeat(60))
        logger.info("SNAPSHOT - Coroutines:")
        session.snapshot.coroutines.values.forEach { 
            logger.info("  📊 ${it.label ?: it.id}: state=${it.state}")
        }
        
        logger.info("\nPROJECTION SERVICE - Hierarchy:")
        session.projectionService.getHierarchyTree().forEach {
            logger.info("  🌲 ${it.name} [${it.state}] - parent: ${it.parentId}, children: ${it.children.size}")
        }
        
        live.cancel()
        logger.info("✅ TEST 3 COMPLETED\n")
    }
    
    /**
     * Test 4: Exception Propagation - Child Failure Cancels Siblings
     * This is THE CRITICAL TEST for structured concurrency!
     */
    suspend fun testExceptionPropagation() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("TEST 4: Exception Propagation - Structured Concurrency")
        logger.info("Expected: Child-2 throws → Parent cancelled → Child-1 cancelled")
        logger.info("=".repeat(60))
        
        val session = VizSession("test-exception-prop")
        
        // Live event logger with focus on failures and cancellations
        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineFailed", "CoroutineCancelled" -> {
                        logger.error("🔴 FAILURE: ${event.kind} | $event")
                    }
                    "CoroutineCreated", "CoroutineStarted" -> {
                        logger.info("🟢 START: ${event.kind} | $event")
                    }
                    "CoroutineCompleted" -> {
                        logger.info("✅ COMPLETE: ${event.kind} | $event")
                    }
                    else -> {
                        logger.debug("📡 ${event.kind} | $event")
                    }
                }
            }
        }
        
        val viz = VizScope(session)
        
        try {
            val job = viz.vizLaunch("parent") {
                logger.info("👨‍👧‍👦 Parent: Launching children...")
                
                // Child 1: Long-running, should be cancelled when child 2 fails
                vizLaunch("child-1-long-running") {
                    logger.info("👶 Child-1: Started, will run for 5 seconds...")
                    try {
                        vizDelay(5000)
                        logger.info("👶 Child-1: Completed successfully (THIS SHOULD NOT PRINT!)")
                    } catch (e: Exception) {
                        logger.warn("👶 Child-1: Cancelled due to sibling failure: ${e.message}")
                        throw e
                    }
                }
                
                // Small delay to ensure child-1 starts
                vizDelay(100)
                
                // Child 2: Will fail after 500ms
                vizLaunch("child-2-will-fail") {
                    logger.info("👶 Child-2: Started, will fail in 500ms...")
                    vizDelay(500)
                    logger.error("👶 Child-2: Throwing exception NOW!")
                    throw RuntimeException("Child-2 intentional failure for testing!")
                }
                
                logger.info("👨‍👧‍👦 Parent: Waiting for children... (THIS SHOULD NOT COMPLETE)")
            }
            
            // Wait and observe
            delay(3000)
            logger.info("⏰ Main: Waited 3 seconds")
            
        } catch (e: Exception) {
            logger.error("❌ Main: Caught exception from parent job: ${e.message}")
        }
        
        logger.info("\n" + "=".repeat(60))
        logger.info("FINAL SNAPSHOT - Verify Cancellation Propagation:")
        session.snapshot.coroutines.values.forEach { node ->
            val emoji = when (node.state) {
                CoroutineState.COMPLETED -> "✅"
                CoroutineState.CANCELLED -> "🚫"
                CoroutineState.FAILED -> "❌"
                else -> "❓"
            }
            logger.info("  $emoji ${node.label ?: node.id}: state=${node.state}")
        }
        
        logger.info("\nPROJECTION SERVICE - Hierarchy:")
        session.projectionService.getHierarchyTree().forEach {
            logger.info("  🌲 ${it.name} [${it.state}]")
        }
        
        // Verify structured concurrency
        val child1 = session.snapshot.coroutines.values.find { it.label == "child-1-long-running" }
        val child2 = session.snapshot.coroutines.values.find { it.label == "child-2-will-fail" }
        val parent = session.snapshot.coroutines.values.find { it.label == "parent" }
        
        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Child-2 (failed):     ${child2?.state} ${if (child2?.state == CoroutineState.FAILED) "✅" else "❌"}")
        logger.info("  Parent (cancelled):   ${parent?.state} ${if (parent?.state == CoroutineState.CANCELLED) "✅" else "❌"}")
        logger.info("  Child-1 (cancelled):  ${child1?.state} ${if (child1?.state == CoroutineState.CANCELLED) "✅" else "❌"}")
        
        if (child2?.state == CoroutineState.FAILED && parent?.state == CoroutineState.CANCELLED && child1?.state == CoroutineState.CANCELLED) {
            logger.info("🎉 STRUCTURED CONCURRENCY VERIFIED! Exception properly propagated!")
        } else {
            logger.error("⚠️ STRUCTURED CONCURRENCY BROKEN! Check event sequence!")
        }
        
        live.cancel()
        logger.info("✅ TEST 4 COMPLETED\n")
    }
    
    /**
     * Test 5: Mixed Launch + Async with Exception
     * Tests exception propagation with deferred tasks
     */
    suspend fun testMixedLaunchAsyncWithException() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("TEST 5: Mixed Launch + Async with Exception")
        logger.info("Parent launches coroutines AND async tasks, one async task fails")
        logger.info("=".repeat(60))
        
        val session = VizSession("test-mixed-exception")
        
        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineFailed", "CoroutineCancelled" -> {
                        logger.error("🔴 ${event.kind} | $event")
                    }
                    "DeferredValueAvailable" -> {
                        logger.info("✨ DEFERRED VALUE READY | $event")
                    }
                    "DeferredAwaitStarted", "DeferredAwaitCompleted" -> {
                        logger.info("⏳ ${event.kind} | $event")
                    }
                    else -> {
                        logger.debug("📡 ${event.kind}")
                    }
                }
            }
        }
        
        val viz = VizScope(session)
        
        try {
            val job = viz.vizLaunch("parent") {
                logger.info("👨‍👧‍👦 Parent: Starting mixed scenario...")
                
                // Launch a regular coroutine
                vizLaunch("launch-task") {
                    logger.info("🚀 Launch Task: Running...")
                    vizDelay(200)
                    logger.info("🚀 Launch Task: Done")
                }
                
                // Create async task that will succeed
                val asyncSuccess = vizAsync("async-success") {
                    logger.info("⚙️ Async Success: Computing...")
                    vizDelay(100)
                    logger.info("✅ Async Success: Done!")
                    "Success Result"
                }
                
                // Create async task that will FAIL
                val asyncFail = vizAsync("async-fail") {
                    logger.info("⚙️ Async Fail: Computing...")
                    vizDelay(150)
                    logger.error("💥 Async Fail: Throwing exception!")
                    throw RuntimeException("Async task intentional failure!")
                }
                
                // Try to await (this should fail and propagate)
                try {
                    logger.info("⏳ Parent: Awaiting successful async...")
                    val result = asyncSuccess.await()
                    logger.info("✅ Parent: Got result: $result")
                    
                    logger.info("⏳ Parent: Awaiting failing async...")
                    asyncFail.await()  // This will throw!
                    logger.error("❌ THIS SHOULD NOT PRINT - await should have thrown!")
                    
                } catch (e: Exception) {
                    logger.error("💥 Parent: Caught exception from async: ${e.message}")
                    throw e  // Re-throw to propagate cancellation
                }
            }
            
            delay(1000)
            
        } catch (e: Exception) {
            logger.error("❌ Main: Caught exception: ${e.message}")
        }
        
        logger.info("\n" + "=".repeat(60))
        logger.info("FINAL STATE:")
        session.snapshot.coroutines.values.forEach { node ->
            logger.info("  ${node.label ?: node.id}: ${node.state}")
        }
        
        live.cancel()
        logger.info("✅ TEST 5 COMPLETED\n")
    }
    
    /**
     * Test 6: Multiple Async Tasks - Multiple Awaiters
     * Tests that multiple coroutines can await the same deferred
     */
    suspend fun testMultipleAwaiters() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("TEST 6: Multiple Awaiters on Same Deferred")
        logger.info("=".repeat(60))
        
        val session = VizSession("test-multiple-awaiters")
        
        val live = launch {
            session.bus.stream().collect { event ->
                if (event.kind.startsWith("Deferred")) {
                    logger.info("🎯 ${event.kind} | $event")
                }
            }
        }
        
        val viz = VizScope(session)
        
        val job = viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Creating shared async task...")
            
            // Create ONE async task
            val sharedDeferred = vizAsync("shared-computation") {
                logger.info("⚙️ Shared: Starting expensive computation...")
                vizDelay(500)
                logger.info("✅ Shared: Computation complete!")
                42  // The answer!
            }
            
            // Multiple coroutines await the same result
            vizLaunch("awaiter-1") {
                logger.info("👤 Awaiter-1: Waiting for shared result...")
                val result = sharedDeferred.await()
                logger.info("✅ Awaiter-1: Got result: $result")
            }
            
            vizLaunch("awaiter-2") {
                vizDelay(100)  // Start slightly later
                logger.info("👤 Awaiter-2: Waiting for shared result...")
                val result = sharedDeferred.await()
                logger.info("✅ Awaiter-2: Got result: $result")
            }
            
            vizLaunch("awaiter-3") {
                vizDelay(200)  // Start even later
                logger.info("👤 Awaiter-3: Waiting for shared result...")
                val result = sharedDeferred.await()
                logger.info("✅ Awaiter-3: Got result: $result")
            }
            
            logger.info("👨‍👧‍👦 Parent: Launched all awaiters, waiting for completion...")
        }
        
        delay(1500)
        
        logger.info("\n" + "=".repeat(60))
        logger.info("FINAL STATE - All awaiters should have completed:")
        session.snapshot.coroutines.values
            .filter { it.label?.startsWith("awaiter") == true }
            .forEach { logger.info("  ${it.label}: ${it.state}") }
        
        live.cancel()
        logger.info("✅ TEST 6 COMPLETED\n")
    }
    
    /**
     * Master test runner - runs all tests in sequence
     */
    suspend fun runAllTests() = coroutineScope {
        logger.info("\n" + "#".repeat(70))
        logger.info("# COROUTINE VISUALIZER - COMPREHENSIVE TEST SUITE")
        logger.info("# Testing: vizLaunch, vizAsync, Exception Propagation, Structured Concurrency")
        logger.info("#".repeat(70) + "\n")
        
        try {
            // Test 1 & 2 already exist (main, main2)
            logger.info("Skipping Test 1 & 2 (existing main/main2 functions)\n")
            
            // Test 3: Basic async
            testAsyncBasic()
            delay(500)
            
            // Test 4: THE CRITICAL TEST - Exception propagation
            testExceptionPropagation()
            delay(500)
            
            // Test 5: Mixed launch + async with exception
            testMixedLaunchAsyncWithException()
            delay(500)
            
            // Test 6: Multiple awaiters
            testMultipleAwaiters()
            delay(500)
            
            logger.info("\n" + "#".repeat(70))
            logger.info("# ALL TESTS COMPLETED!")
            logger.info("#".repeat(70))
            
        } catch (e: Exception) {
            logger.error("Test suite failed!", e)
        }
    }

    /**
     * Test dispatcher tracking with VizDispatchers.
     * 
     * Demonstrates how to:
     * 1. Create VizDispatchers for a session
     * 2. Use different dispatchers via context
     * 3. Verify dispatcher events are emitted
     */
    suspend fun testDispatcherTracking() = coroutineScope {
        logger.info("\n" + "=".repeat(70))
        logger.info("TEST: Dispatcher Tracking")
        logger.info("=".repeat(70))
        
        val session = VizSession("test-dispatcher-tracking")
        
        // Create instrumented dispatchers
        val dispatchers = VizDispatchers(session, "test")
        
        logger.info("📦 Created VizDispatchers:")
        logger.info("   - default: ${dispatchers.default.dispatcherName}")
        logger.info("   - io: ${dispatchers.io.dispatcherName}")
        
        // Test 1: Default dispatcher
        logger.info("\n🟢 Test 1: Default Dispatcher")
        val scope1 = VizScope(session, context = dispatchers.default)
        scope1.vizLaunch(label = "default-worker") {
            logger.info("   [default-worker] Running on: ${Thread.currentThread().name}")
            vizDelay(100)
            logger.info("   [default-worker] Completed")
        }.join()
        
        // Test 2: IO dispatcher
        logger.info("\n🔵 Test 2: IO Dispatcher")
        val scope2 = VizScope(session, context = dispatchers.io)
        scope2.vizLaunch(label = "io-worker") {
            logger.info("   [io-worker] Running on: ${Thread.currentThread().name}")
            vizDelay(100)
            logger.info("   [io-worker] Completed")
        }.join()
        
        // Test 3: Mixed dispatchers
        logger.info("\n🟡 Test 3: Mixed Dispatchers in Same Scope")
        val scope3 = VizScope(session, context = dispatchers.default)
        scope3.vizLaunch(label = "parent") {
            logger.info("   [parent] Running on: ${Thread.currentThread().name}")
            
            // Child on Default (inherited)
            vizLaunch(label = "child-default") {
                logger.info("   [child-default] Running on: ${Thread.currentThread().name}")
                vizDelay(100)
            }
            
            // Child on IO (explicit context)
            vizLaunch(label = "child-io", context = dispatchers.io) {
                logger.info("   [child-io] Running on: ${Thread.currentThread().name}")
                vizDelay(100)
            }
        }.join()
        
        // Verify events
        logger.info("\n📊 Event Summary:")
        val events = session.store.all()
        val dispatcherEvents = events.filterIsInstance<com.jh.proj.coroutineviz.events.DispatcherSelected>()
        val threadEvents = events.filterIsInstance<com.jh.proj.coroutineviz.events.ThreadAssigned>()
        
        logger.info("   Total events: ${events.size}")
        logger.info("   DispatcherSelected events: ${dispatcherEvents.size}")
        logger.info("   ThreadAssigned events: ${threadEvents.size}")
        
        if (dispatcherEvents.isEmpty()) {
            logger.warn("\n⚠️  WARNING: No DispatcherSelected events found!")
            logger.warn("   Dispatcher tracking may not be working correctly.")
        } else {
            logger.info("\n✅ SUCCESS: Dispatcher tracking is working!")
            logger.info("   Found ${dispatcherEvents.size} dispatcher selection events")
        }
        
        logger.info("\n" + "=".repeat(70))
        logger.info("✅ Dispatcher Test Complete!")
        logger.info("=".repeat(70))
    }

    companion object {
        val logger = LoggerFactory.getLogger(VizEventMain::class.java)
    }
}