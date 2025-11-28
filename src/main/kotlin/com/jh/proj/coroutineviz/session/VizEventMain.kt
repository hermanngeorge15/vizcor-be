package com.jh.proj.coroutineviz.session

import com.jh.proj.coroutineviz.extension.getLabel
import com.jh.proj.coroutineviz.wrappers.VizDispatchers
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class VizEventMain {


    suspend fun exampleSimpleLaunchVisualization() = coroutineScope {
        logger.info("\n" + "=".repeat(60))
        logger.info("TEST 1: Simple vizLaunch - Parent + Single Child")
        logger.info("Goal: ensure parent + child complete with proper events")
        logger.info("=".repeat(60))
        val session = VizSession(sessionId = "session-one-launch")
        val eventLog = mutableSetOf<String>()
        val liveLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineCompleted" -> {
                        eventLog.add(event.getLabel() ?: "unknown")
                    }
                }
            }
        }

        val exampleDelay = 1000L

        val scope = VizScope(session)
        scope.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Starting child launches...")
            vizLaunch("child-1") {
                logger.info("🌱 Child-1: Running for ${exampleDelay}ms...")
                delay(exampleDelay)
                logger.info("✅ Child-1: Finished work")
            }
            logger.info("🎉 Parent: Completed")
        }

        delay(1001)
        val eventLogExcepted = setOf("parent", "child-1")
        val expected = eventLogExcepted.all { eventLog.contains(it) }
        logger.info("🧪 Expected labels: $eventLogExcepted")
        logger.info("📒 eventLog contains all labels? $expected")
        logger.info("=== SNAPSHOT ===")
        session.snapshot.coroutines.values.forEach { logger.info(it.toString()) }
        // Clean up
        liveLogger.cancel()
    }

    suspend fun exampleOfTwoLanuches() = coroutineScope {
        logger.info("\n" + "=".repeat(60))
        logger.info("TEST 2: Parallel vizLaunch - Parent + Two Children")
        logger.info("Goal: observe concurrent children finishing independently")
        logger.info("=".repeat(60))
        val session = VizSession(sessionId = "session-one-launch")
        val eventLog = mutableSetOf<String>()
        val liveLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineCompleted" -> {
                        eventLog.add(event.getLabel() ?: "unknown")
//                        logger.info("📡 EVENT: ${event.kind} | ${event.seq} | $event")
                    }
                }
            }
        }

        val exampleDelay = 1600L

        val scope = VizScope(session)
        scope.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Launching children in parallel...")
            vizLaunch("child-1") {
                logger.info("🌿 Child-1: Starting 1s task...")
                delay(1000)
                logger.info("✅ Child-1: Finished 1s task")
            }
            vizLaunch("child-2") {
                logger.info("🌳 Child-2: Starting 1.5s task...")
                delay(1500)
                logger.info("✅ Child-2: Finished 1.5s task")
            }
            logger.info("🎉 Parent: All children launched, awaiting completion")

        }

        delay(exampleDelay)
        val eventLogExcepted = setOf("parent", "child-1", "child-2")
        val expected = eventLogExcepted.all { eventLog.contains(it) }
        logger.info("🧪 Expected labels: $eventLogExcepted")
        logger.info("📒 eventLog contains all labels? $expected")
        logger.info("=== SNAPSHOT ===")
        session.snapshot.coroutines.values.forEach { logger.info(it.toString()) }
        // Clean up
        liveLogger.cancel()
    }

    suspend fun exampleOfNestedLaunches() = coroutineScope {
        logger.info("\n" + "=".repeat(60))
        logger.info("TEST 3: Nested vizLaunch hierarchy")
        logger.info("Goal: visualize parent/child/grandchild coordination")
        logger.info("=".repeat(60))

        val session = VizSession("session-A")

        val eventLog = mutableListOf<String>()
        // Subscribe to live events
        val live = launch {
            session.bus.stream().collect { event ->
                //  logger.info("LIVE: $event")
                when (event.kind) {
                    "CoroutineCompleted" -> {
                        eventLog.add(event.getLabel() ?: "unknown")
                        //            logger.info("📡 EVENT: ${event.kind} | ${event.seq} | $event")
                    }
                }
            }
        }

        val viz = VizScope(session)

        // Launch the visualization hierarchy - runs independently of main2's scope
        viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Building nested tree...")
            vizLaunch("child-1") {
                logger.info("🌿 Child-1: Starting and spawning grandchild...")
                vizLaunch("child-1-1") {
                    logger.info("🍃 Child-1-1: Performing 2s task...")
                    vizDelay(2000)
                    logger.info("✅ Child-1-1: Completed work")
                }
                vizDelay(2000)
                logger.info("✅ Child-1: Completed after grandchild")
            }
            vizLaunch("child-2") {
                logger.info("🌲 Child-2: Starting branch with grandchild...")
                vizLaunch("child-2-1") {
                    logger.info("🍂 Child-2-1: Performing 1.5s task...")
                    vizDelay(1500)
                    logger.info("✅ Child-2-1: Completed work")
                }
                logger.info("✅ Child-2: Completed after child-2-1")
                vizDelay(1500)
            }
            logger.info("👨‍👧‍👦 Parent: finished...")

        }

        // Wait for the scenario to complete
        delay(5000)
        val eventLogExpected = setOf("parent", "child-2-1", "child-2", "child-1", "child-1-1")
        val expected = eventLogExpected.all { eventLog.contains(it) }
        logger.info("🧪 Expected labels: $eventLogExpected")
        logger.info("📒 eventLog contains all labels? $expected")
        logger.info("=== SNAPSHOT ===")
        session.snapshot.coroutines.values.forEach { logger.info(it.toString()) }

        // Clean up
        live.cancel()
    }

    /**
     * Test 3: Basic vizAsync functionality
     * Tests async/await pattern with proper event tracking
     */
    suspend fun exampleOfTwoAsyncs() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("TEST 3: Basic vizAsync - Async/Await Pattern")
        logger.info("=".repeat(60))

        val session = VizSession("test-async-basic")

        val eventLog = mutableSetOf<String>()
        // Live event logger
        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineCompleted" -> {
                        eventLog.add(event.getLabel() ?: "unknown")
                        logger.info("📡 EVENT: ${event.kind} | ${event.seq} | $event")
                    }
                }
            }
        }

        val viz = VizScope(session)

        // Launch parent that creates async tasks
        viz.vizLaunch("parent") {
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

        val parentName = "parent"
        val launchLongRunningTaskName = "child-1-long-running"
        val vizLaunch2ExceptionName = "child-2-will-fail"

        viz.vizLaunch(parentName) {
            logger.info("👨‍👧‍👦 Parent: Launching children...")

            // Child 1: Long-running, should be cancelled when child 2 fails
            vizLaunch(launchLongRunningTaskName) {
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

            vizLaunch(vizLaunch2ExceptionName) {
                logger.info("👶 Child-2: Started, will fail in 500ms...")
                vizDelay(500)
                logger.error("👶 Child-2: Throwing exception NOW! $vizLaunch2ExceptionName")
                throw RuntimeException("Child-2 intentional failure for testing! $vizLaunch2ExceptionName")
            }

            logger.info("👨‍👧‍👦 Parent: Waiting for children... (THIS SHOULD NOT COMPLETE)")
        }

        // Wait and observe
        delay(3000)
        logger.info("⏰ Main: Waited 3 seconds")


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
            viz.vizLaunch("parent") {
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

        viz.vizLaunch("parent") {
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
            exampleOfTwoAsyncs()
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

    // ========================================================================
    // JOB STATUS TRACKING TESTS - WaitingForChildren & Structured Concurrency
    // ========================================================================

    /**
     * Test 7: Basic Waiting for Children
     * Tests the WaitingForChildren event emission and job status tracking
     */
    suspend fun testWaitingForChildren() = coroutineScope {
        logger.info("\n" + "=".repeat(70))
        logger.info("TEST 7: Waiting for Children - Job Status Tracking")
        logger.info("Goal: Parent body completes but waits for 3 children")
        logger.info("=".repeat(70))

        val session = VizSession("test-waiting-for-children")
        
        // Enable job monitoring for real-time status updates
        session.enableJobMonitoring()

        // Track specific events we care about
        val waitingEvents = mutableListOf<String>()
        val jobStateEvents = mutableListOf<String>()
        
        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "WaitingForChildren" -> {
                        logger.info("⏳ WAITING EVENT | $event")
                        waitingEvents.add(event.getLabel() ?: "unknown")
                    }
                    "JobStateChanged" -> {
                        logger.info("📊 JOB STATE | $event")
                        jobStateEvents.add(event.getLabel() ?: "unknown")
                    }
                    "CoroutineBodyCompleted" -> {
                        logger.info("🏁 BODY DONE | ${event.getLabel()}")
                    }
                    "CoroutineCompleted" -> {
                        logger.info("✅ COMPLETED | ${event.getLabel()}")
                    }
                }
            }
        }

        val viz = VizScope(session)

      val job =  viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Launching 3 children with different durations...")
            
            vizLaunch("child-1-fast") {
                logger.info("🏃 Child-1: Running fast task (500ms)...")
                vizDelay(500)
                logger.info("✅ Child-1: Done!")
            }
            
            vizLaunch("child-2-medium") {
                logger.info("🚶 Child-2: Running medium task (1000ms)...")
                vizDelay(1000)
                logger.info("✅ Child-2: Done!")
            }
            
            vizLaunch("child-3-slow") {
                logger.info("🐌 Child-3: Running slow task (1500ms)...")
                vizDelay(5000)
                logger.info("✅ Child-3: Done!")
            }
            
            logger.info("👨‍👧‍👦 Parent: Body finished! Now waiting for children...")
            // Parent body ends here, but won't complete until all children finish
        }

        // Wait for everything to complete
        delay(2000)

        logger.info("\n" + "=".repeat(70))
        logger.info("VERIFICATION:")
        logger.info("  WaitingForChildren events: ${waitingEvents.size} (expected: 1)")
        logger.info("  Parent should have emitted WaitingForChildren: ${waitingEvents.contains("parent")}")
        
        logger.info("\n🔍 FINAL STATES:")
        session.snapshot.coroutines.values.forEach { node ->
            logger.info("  ${node.label ?: node.id}: ${node.state}")
        }

        logger.info("\n📊 HIERARCHY:")
        session.projectionService.getHierarchyTree().forEach { node ->
            logger.info("  ${node.name} [${node.state}] - children: ${node.children.size}, active: ${node.activeChildrenCount}")
        }

        val success = waitingEvents.contains("parent")
        if (success) {
            logger.info("\n✅ SUCCESS: WaitingForChildren event properly emitted!")
        } else {
            logger.error("\n❌ FAILURE: WaitingForChildren event NOT emitted!")
        }

        live.cancel()
        session.close()
        job.cancel()
        logger.info("✅ TEST 7 COMPLETED\n")
    }

    /**
     * Test 8: Nested Waiting for Children
     * Tests hierarchical waiting - parent waits for children who also wait for their children
     */
    suspend fun testNestedWaitingForChildren() = coroutineScope {
        logger.info("\n" + "=".repeat(70))
        logger.info("TEST 8: Nested Waiting - Multi-level Hierarchy")
        logger.info("Goal: L1 waits for L2, L2 waits for L3")
        logger.info("=".repeat(70))

        val session = VizSession("test-nested-waiting")
        session.enableJobMonitoring()

        val waitingCoroutines = mutableSetOf<String>()
        
        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "WaitingForChildren" -> {
                        val label = event.getLabel() ?: "unknown"
                        waitingCoroutines.add(label)
                        logger.info("⏳ $label is WAITING for children")
                    }
                    "CoroutineCompleted" -> {
                        logger.info("✅ ${event.getLabel()} COMPLETED")
                    }
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("level-1") {
            logger.info("🏢 L1: Starting...")
            
            vizLaunch("level-2-A") {
                logger.info("  🏬 L2-A: Starting...")
                
                vizLaunch("level-3-A1") {
                    logger.info("    🏪 L3-A1: Working for 800ms...")
                    vizDelay(800)
                    logger.info("    ✅ L3-A1: Done")
                }
                
                vizLaunch("level-3-A2") {
                    logger.info("    🏪 L3-A2: Working for 1200ms...")
                    vizDelay(1200)
                    logger.info("    ✅ L3-A2: Done")
                }
                
                logger.info("  🏬 L2-A: Body done, waiting for grandchildren...")
            }
            
            vizLaunch("level-2-B") {
                logger.info("  🏬 L2-B: Starting...")
                
                vizLaunch("level-3-B1") {
                    logger.info("    🏪 L3-B1: Working for 600ms...")
                    vizDelay(600)
                    logger.info("    ✅ L3-B1: Done")
                }
                
                logger.info("  🏬 L2-B: Body done, waiting for grandchild...")
            }
            
            logger.info("🏢 L1: Body done, waiting for all descendants...")
        }

        delay(1500)

        logger.info("\n" + "=".repeat(70))
        logger.info("VERIFICATION:")
        logger.info("  Coroutines that waited: $waitingCoroutines")
        logger.info("  Expected: level-1, level-2-A, level-2-B")
        
        val expectedWaiters = setOf("level-1", "level-2-A", "level-2-B")
        val allWaited = expectedWaiters.all { waitingCoroutines.contains(it) }
        
        if (allWaited) {
            logger.info("\n✅ SUCCESS: All levels properly waited for children!")
        } else {
            logger.error("\n❌ FAILURE: Some levels did not wait. Missing: ${expectedWaiters - waitingCoroutines}")
        }

        live.cancel()
        session.close()
        logger.info("✅ TEST 8 COMPLETED\n")
    }

    /**
     * Test 9: Waiting with Mixed Async and Launch
     * Tests waiting when parent has both launch and async children
     */
    suspend fun testWaitingWithMixedChildren() = coroutineScope {
        logger.info("\n" + "=".repeat(70))
        logger.info("TEST 9: Waiting with Mixed Launch + Async")
        logger.info("Goal: Parent waits for both launch and async children")
        logger.info("=".repeat(70))

        val session = VizSession("test-mixed-waiting")
        session.enableJobMonitoring()

        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "WaitingForChildren" -> {
                        logger.info("⏳ ${event.getLabel()} waiting | $event")
                    }
                    "DeferredValueAvailable" -> {
                        logger.info("📦 Async result ready | $event")
                    }
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Starting mixed children...")
            
            // Regular launch
            vizLaunch("launch-child") {
                logger.info("🚀 Launch child: Working...")
                vizDelay(800)
                logger.info("✅ Launch child: Done")
            }
            
            // Async children (not awaited in parent body)
            val async1 = vizAsync("async-child-1") {
                logger.info("⚙️ Async-1: Computing...")
                vizDelay(1000)
                logger.info("✅ Async-1: Done")
                "Result-1"
            }
            
            val async2 = vizAsync("async-child-2") {
                logger.info("⚙️ Async-2: Computing...")
                vizDelay(1200)
                logger.info("✅ Async-2: Done")
                "Result-2"
            }
            
            logger.info("👨‍👧‍👦 Parent: Body finished, waiting for all children...")
            // Parent waits for BOTH launch and async children even without await!
        }

        delay(1500)

        logger.info("\n" + "=".repeat(70))
        logger.info("VERIFICATION:")
        logger.info("  All children should have completed")
        
        session.snapshot.coroutines.values
            .filter { it.label?.contains("child") == true }
            .forEach { 
                logger.info("  ${it.label}: ${it.state}")
            }

        live.cancel()
        session.close()
        logger.info("✅ TEST 9 COMPLETED\n")
    }

    /**
     * Test 10: Cancellation During Waiting
     * Tests what happens when parent is cancelled while waiting for children
     */
    suspend fun testCancellationDuringWaiting() = coroutineScope {
        logger.info("\n" + "=".repeat(70))
        logger.info("TEST 10: Cancellation During Waiting")
        logger.info("Goal: Parent cancelled while waiting → children also cancelled")
        logger.info("=".repeat(70))

        val session = VizSession("test-cancel-waiting")
        session.enableJobMonitoring()

        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "WaitingForChildren" -> {
                        logger.info("⏳ ${event.getLabel()} waiting")
                    }
                    "CoroutineCancelled" -> {
                        logger.info("🚫 ${event.getLabel()} CANCELLED")
                    }
                }
            }
        }

        val viz = VizScope(session)

        val parentJob = viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Launching long-running children...")
            
            vizLaunch("child-1") {
                logger.info("🌱 Child-1: Starting long task...")
                try {
                    vizDelay(5000)  // Very long
                    logger.error("❌ Child-1: Should not complete!")
                } catch (e: Exception) {
                    logger.info("🚫 Child-1: Cancelled as expected")
                    throw e
                }
            }
            
            vizLaunch("child-2") {
                logger.info("🌱 Child-2: Starting long task...")
                try {
                    vizDelay(5000)  // Very long
                    logger.error("❌ Child-2: Should not complete!")
                } catch (e: Exception) {
                    logger.info("🚫 Child-2: Cancelled as expected")
                    throw e
                }
            }
            
            logger.info("👨‍👧‍👦 Parent: Body done, waiting...")
        }

        // Let parent enter waiting state
        delay(200)
        
        logger.info("\n💥 CANCELLING PARENT NOW!")
        parentJob.cancel()
        
        delay(500)

        logger.info("\n" + "=".repeat(70))
        logger.info("VERIFICATION - All should be CANCELLED:")
        session.snapshot.coroutines.values.forEach { node ->
            val emoji = if (node.state == CoroutineState.CANCELLED) "✅" else "❌"
            logger.info("  $emoji ${node.label}: ${node.state}")
        }

        val allCancelled = session.snapshot.coroutines.values.all { 
            it.state == CoroutineState.CANCELLED 
        }

        if (allCancelled) {
            logger.info("\n✅ SUCCESS: Cancellation properly propagated!")
        } else {
            logger.error("\n❌ FAILURE: Some coroutines not cancelled!")
        }

        live.cancel()
        session.close()
        logger.info("✅ TEST 10 COMPLETED\n")
    }

    /**
     * Test 11: Progress Tracking
     * Tests that we can track children completion progress over time
     */
    suspend fun testProgressTracking() = coroutineScope {
        logger.info("\n" + "=".repeat(70))
        logger.info("TEST 11: Progress Tracking - Children Complete Over Time")
        logger.info("Goal: Monitor parent's active children count decreasing")
        logger.info("=".repeat(70))

        val session = VizSession("test-progress-tracking")
        session.enableJobMonitoring()

        val progressUpdates = mutableListOf<Pair<Long, Int>>()  // (timestamp, activeCount)
        
        val live = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "WaitingForChildren" -> {
                        val evt = event as com.jh.proj.coroutineviz.events.WaitingForChildren
                        val timestamp = System.currentTimeMillis()
                        progressUpdates.add(timestamp to evt.activeChildrenCount)
                        logger.info("📊 PROGRESS: ${evt.activeChildrenCount} children still active at ${timestamp}")
                    }
                    "JobStateChanged" -> {
                        val evt = event as com.jh.proj.coroutineviz.events.JobStateChanged
                        if (evt.label == "parent") {
                            logger.info("📊 JOB STATE: parent has ${evt.childrenCount} children")
                        }
                    }
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Launching staggered children...")
            
            vizLaunch("child-1") {
                logger.info("🏃 Child-1: 300ms")
                vizDelay(300)
                logger.info("✅ Child-1: Done")
            }
            
            vizLaunch("child-2") {
                logger.info("🏃 Child-2: 600ms")
                vizDelay(600)
                logger.info("✅ Child-2: Done")
            }
            
            vizLaunch("child-3") {
                logger.info("🏃 Child-3: 900ms")
                vizDelay(900)
                logger.info("✅ Child-3: Done")
            }
            
            vizLaunch("child-4") {
                logger.info("🏃 Child-4: 1200ms")
                vizDelay(1200)
                logger.info("✅ Child-4: Done")
            }
            
            logger.info("👨‍👧‍👦 Parent: Waiting for 4 children...")
        }

        delay(1500)

        logger.info("\n" + "=".repeat(70))
        logger.info("PROGRESS UPDATES:")
        progressUpdates.forEach { (time, count) ->
            logger.info("  Time: $time, Active children: $count")
        }

        logger.info("\n📊 Expected progression: 4 → 3 → 2 → 1 → 0")
        logger.info("   Actual updates: ${progressUpdates.size}")

        live.cancel()
        session.close()
        logger.info("✅ TEST 11 COMPLETED\n")
    }

    /**
     * Master test runner for Job Status tests
     */
    suspend fun runJobStatusTests() = coroutineScope {
        logger.info("\n" + "#".repeat(80))
        logger.info("# JOB STATUS TRACKING TEST SUITE")
        logger.info("# Testing: WaitingForChildren, JobStateChanged, Progress Tracking")
        logger.info("#".repeat(80) + "\n")

        try {
            // Test 7: Basic waiting
            testWaitingForChildren()
            delay(500)

            // Test 8: Nested waiting
            testNestedWaitingForChildren()
            delay(500)

            // Test 9: Mixed async + launch
            testWaitingWithMixedChildren()
            delay(500)

            // Test 10: Cancellation during waiting
            testCancellationDuringWaiting()
            delay(500)

            // Test 11: Progress tracking
            testProgressTracking()
            delay(500)

            logger.info("\n" + "#".repeat(80))
            logger.info("# ALL JOB STATUS TESTS COMPLETED!")
            logger.info("#".repeat(80))

        } catch (e: Exception) {
            logger.error("Job status test suite failed!", e)
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(VizEventMain::class.java)
    }
}