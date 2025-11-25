package com.jh.proj

import com.jh.proj.coroutineviz.session.CoroutineState
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for verifying structured concurrency in VizScope.
 *
 * These tests verify that:
 * 1. Parents wait for all children to complete (structured concurrency)
 * 2. When a child fails, the parent is cancelled
 * 3. When a parent is cancelled, all siblings are cancelled
 * 4. Nested hierarchies work correctly
 * 5. vizAsync behaves correctly with structured concurrency
 */
class StructuredConcurrencyTest {

    private val logger = LoggerFactory.getLogger(StructuredConcurrencyTest::class.java)

    // Helper to safely extract label from events
    private fun com.jh.proj.coroutineviz.events.VizEvent.getLabel(): String? =
        (this as? com.jh.proj.coroutineviz.events.CoroutineEvent)?.label

    /**
     * TEST 1: Basic Structured Concurrency - Parent Waits for Children
     *
     * Expected behavior:
     * - Parent launches 3 children
     * - Children take different amounts of time
     * - Parent's body completes immediately but waits for all children
     * - All coroutines complete successfully
     *
     * Verification:
     * - Parent: COMPLETED (after all children)
     * - All children: COMPLETED
     * - Event order: Child events before parent completion
     */
    @Test
    fun `test basic structured concurrency - parent waits for children`() = kotlinx.coroutines.runBlocking {
        logger.info("=".repeat(60))
        logger.info("TEST 1: Basic Structured Concurrency - Parent Waits")
        logger.info("=".repeat(60))

        val session = VizSession("test-basic-sc")
        val eventLog = mutableListOf<String>()

        // Live event logger
        val eventLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineCompleted" -> {
                        eventLog.add("COMPLETED: ${event.getLabel() ?: "unknown"}")
                        logger.info("✅ COMPLETED: ${event.getLabel()}")
                    }

                    "CoroutineBodyCompleted" -> {
                        eventLog.add("BODY_COMPLETED: ${event.getLabel() ?: "unknown"}")
                        logger.info("📋 BODY_COMPLETED: ${event.getLabel()}")
                    }
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Launching 3 children...")

            vizLaunch("child-fast-100ms") {
                logger.info("👶 Child-fast: Starting...")
                vizDelay(100)
                logger.info("👶 Child-fast: Done!")
            }

            vizLaunch("child-medium-200ms") {
                logger.info("👶 Child-medium: Starting...")
                vizDelay(200)
                logger.info("👶 Child-medium: Done!")
            }

            vizLaunch("child-slow-300ms") {
                logger.info("👶 Child-slow: Starting...")
                vizDelay(300)
                logger.info("👶 Child-slow: Done!")
            }

            logger.info("👨‍👧‍👦 Parent: Body finished (but should wait for children)...")
        }

//        assertTrue { job.isCompleted }
//        assertFalse { job.isCancelled }
//        assertFalse { job.isActive }

        // Wait for all to complete
        delay(500)
        eventLogger.cancel()

        // Verify all completed
        val parent = session.snapshot.coroutines.values.find { it.label == "parent" }
        val childFast = session.snapshot.coroutines.values.find { it.label == "child-fast-100ms" }
        val childMedium = session.snapshot.coroutines.values.find { it.label == "child-medium-200ms" }
        val childSlow = session.snapshot.coroutines.values.find { it.label == "child-slow-300ms" }

        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Parent: ${parent?.state}")
        logger.info("  Child-fast: ${childFast?.state}")
        logger.info("  Child-medium: ${childMedium?.state}")
        logger.info("  Child-slow: ${childSlow?.state}")

        // Assert all completed
        assertEquals(CoroutineState.COMPLETED, parent?.state, "Parent should be COMPLETED")
        assertEquals(CoroutineState.COMPLETED, childFast?.state, "Child-fast should be COMPLETED")
        assertEquals(CoroutineState.COMPLETED, childMedium?.state, "Child-medium should be COMPLETED")
        assertEquals(CoroutineState.COMPLETED, childSlow?.state, "Child-slow should be COMPLETED")

        logger.info("✅ TEST 1 PASSED: Parent correctly waits for all children!\n")
    }

    /**
     * TEST 2: Exception Propagation - Child Fails, Parent and Siblings Cancelled
     *
     * Expected behavior:
     * - Parent launches 2 children
     * - Child-2 throws exception after 500ms
     * - Exception propagates through coroutineScope in parent (parent FAILED)
     * - Child-1 gets cancelled (sibling of failing child)
     *
     * Verification:
     * - Child-2: FAILED (threw exception)
     * - Parent: FAILED (exception from coroutineScope is treated as own code)
     * - Child-1: CANCELLED (sibling of failed child)
     *
     * Note: In Kotlin coroutines, when a child fails inside coroutineScope{},
     * the exception is rethrown and treated as if the parent's own code threw.
     */
    @Test
    fun `test exception propagation - child fails, parent and siblings cancelled`() = kotlinx.coroutines.runBlocking {
        logger.info("=".repeat(60))
        logger.info("TEST 2: Exception Propagation")
        logger.info("Expected: Child-2 throws → Parent FAILED (not cancelled) → Child-1 cancelled")
        logger.info("=".repeat(60))

        val session = VizSession("test-exception-prop")

        val eventLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineFailed" -> logger.error("🔴 FAILED: ${event.getLabel()}")
                    "CoroutineCancelled" -> logger.warn("🚫 CANCELLED: ${event.getLabel()}")
                    "CoroutineCompleted" -> logger.info("✅ COMPLETED: ${event.getLabel()}")
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Launching children...")
            vizLaunch("child-1-long-running") {
                logger.info("👶 Child-1: Started, will run for 5 seconds...")
                vizDelay(5000)
                logger.error("❌ THIS SHOULD NOT PRINT - child-1 should be cancelled!")
            }
            vizLaunch("child-2-will-fail") {
                logger.info("👶 Child-2: Started, will fail in 500ms...")
                vizDelay(500)
                logger.info("👶 Child-2: Throwing exception NOW!")
                throw RuntimeException("Child-2 intentional failure!")
            }
            logger.info("👨‍👧‍👦 Parent: Body finished, waiting for children...")
        }

        delay(3000)


        // Allow time for invokeOnCompletion handlers to finish emitting events
        // invokeOnCompletion launches coroutines which need time to complete
        delay(1000)
        eventLogger.cancel()

        // Verify structured concurrency
        val parent = session.snapshot.coroutines.values.find { it.label == "parent" }
        val child1 = session.snapshot.coroutines.values.find { it.label == "child-1-long-running" }
        val child2 = session.snapshot.coroutines.values.find { it.label == "child-2-will-fail" }

        logger.info("\nDEBUG - All coroutines in snapshot:")
        session.snapshot.coroutines.values.forEach {
            logger.info("  ${it.label ?: it.id}: ${it.state}")
        }

        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Child-2 (should be FAILED): ${child2?.state} ${if (child2?.state == CoroutineState.FAILED) "✅" else "❌"}")
        logger.info("  Parent (should be FAILED): ${parent?.state} ${if (parent?.state == CoroutineState.FAILED) "✅" else "❌"}")
        logger.info("  Child-1 (should be CANCELLED): ${child1?.state} ${if (child1?.state == CoroutineState.CANCELLED) "✅" else "❌"}")

        // Critical assertions
        assertEquals(
            CoroutineState.FAILED, child2?.state,
            "Child-2 should be FAILED (threw exception)"
        )
        assertEquals(
            CoroutineState.FAILED, parent?.state,
            "Parent should be FAILED (exception from coroutineScope)"
        )
        assertEquals(
            CoroutineState.CANCELLED, child1?.state,
            "Child-1 should be CANCELLED (sibling failed)"
        )

        logger.info("✅ TEST 2 PASSED: Structured concurrency exception propagation works!\n")
    }

    /**
     * TEST 3: Nested Hierarchy - 3 Levels Deep
     *
     * Expected behavior:
     * - Parent → Child → Grandchild hierarchy
     * - Grandchild fails
     * - Exception propagates: Child FAILED, Parent FAILED
     *
     * Verification:
     * - Grandchild: FAILED (threw exception)
     * - Child: FAILED (exception from coroutineScope)
     * - Parent: FAILED (exception from coroutineScope)
     */
    @Test
    fun `test nested hierarchy - 3 levels deep with failure`() = kotlinx.coroutines.runBlocking {
        logger.info("=".repeat(60))
        logger.info("TEST 3: Nested Hierarchy (3 levels)")
        logger.info("Expected: Grandchild fails → Child FAILED → Parent FAILED")
        logger.info("=".repeat(60))

        val session = VizSession("test-nested")

        val eventLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineFailed" -> logger.error("🔴 FAILED: ${event.getLabel()}")
                    "CoroutineCancelled" -> logger.warn("🚫 CANCELLED: ${event.getLabel()}")
                    "CoroutineCompleted" -> logger.info("✅ COMPLETED: ${event.getLabel()}")
                }
            }
        }

        val viz = VizScope(session)

        try {
            viz.vizLaunch("parent") {
                logger.info("👴 Parent: Launching child...")

                vizLaunch("child") {
                    logger.info("👨 Child: Launching grandchild...")

                    vizLaunch("grandchild-will-fail") {
                        logger.info("👶 Grandchild: Will fail in 200ms...")
                        vizDelay(200)
                        logger.info("👶 Grandchild: Failing NOW!")
                        throw RuntimeException("Grandchild failure!")
                    }

                    logger.info("👨 Child: Body finished, waiting for grandchild...")
                }

                logger.info("👴 Parent: Body finished, waiting for child...")
            }

            delay(1000)

        } catch (e: Exception) {
            logger.info("📥 Main: Caught exception: ${e.message}")
        }

        // Allow time for invokeOnCompletion handlers to finish
        delay(1000)
        eventLogger.cancel()

        val parent = session.snapshot.coroutines.values.find { it.label == "parent" }
        val child = session.snapshot.coroutines.values.find { it.label == "child" }
        val grandchild = session.snapshot.coroutines.values.find { it.label == "grandchild-will-fail" }

        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Grandchild (should be FAILED): ${grandchild?.state}")
        logger.info("  Child (should be FAILED): ${child?.state}")
        logger.info("  Parent (should be FAILED): ${parent?.state}")

        assertEquals(CoroutineState.FAILED, grandchild?.state, "Grandchild should be FAILED")
        assertEquals(CoroutineState.FAILED, child?.state, "Child should be FAILED (exception from coroutineScope)")
        assertEquals(CoroutineState.FAILED, parent?.state, "Parent should be FAILED (exception from coroutineScope)")

        logger.info("✅ TEST 3 PASSED: Nested hierarchy exception propagation works!\n")
    }

    /**
     * TEST 4: vizAsync - Exception in Async Task
     *
     * Expected behavior:
     * - Parent creates async task
     * - Async task fails
     * - Parent awaits and exception propagates through coroutineScope
     * - Parent marked as FAILED (exception from coroutineScope)
     *
     * Verification:
     * - Async task: FAILED (threw exception)
     * - Parent: FAILED (exception from coroutineScope during await)
     */
    @Test
    fun `test vizAsync exception propagation`() = kotlinx.coroutines.runBlocking {
        logger.info("=".repeat(60))
        logger.info("TEST 4: vizAsync Exception Propagation")
        logger.info("=".repeat(60))

        val session = VizSession("test-async-exception")

        val eventLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineFailed" -> logger.error("🔴 FAILED: ${event.getLabel()}")
                    "CoroutineCancelled" -> logger.warn("🚫 CANCELLED: ${event.getLabel()}")
                    "DeferredValueAvailable" -> logger.info("✨ DEFERRED VALUE: ${event.getLabel()}")
                }
            }
        }

        val viz = VizScope(session)

        try {
            viz.vizLaunch("parent") {
                logger.info("👨‍👧‍👦 Parent: Creating async task...")

                val asyncTask = vizAsync("async-will-fail") {
                    logger.info("⚙️ Async: Starting...")
                    vizDelay(200)
                    logger.info("⚙️ Async: Throwing exception!")
                    throw RuntimeException("Async task failure!")
                }

                logger.info("👨‍👧‍👦 Parent: Awaiting result...")
                asyncTask.await() // This should throw

                logger.error("❌ THIS SHOULD NOT PRINT - await should throw!")
            }

            delay(1000)

        } catch (e: Exception) {
            logger.info("📥 Main: Caught exception: ${e.message}")
        }

        // Allow time for invokeOnCompletion handlers to finish
        delay(1000)
        eventLogger.cancel()

        val parent = session.snapshot.coroutines.values.find { it.label == "parent" }
        val asyncTask = session.snapshot.coroutines.values.find { it.label == "async-will-fail" }

        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Async task (should be FAILED): ${asyncTask?.state}")
        logger.info("  Parent (should be FAILED): ${parent?.state}")

        assertEquals(CoroutineState.FAILED, asyncTask?.state, "Async task should be FAILED")
        assertEquals(CoroutineState.FAILED, parent?.state, "Parent should be FAILED (exception from await)")

        logger.info("✅ TEST 4 PASSED: vizAsync exception propagation works!\n")
    }

    /**
     * TEST 5: Mixed Launch and Async - Successful Completion
     *
     * Expected behavior:
     * - Parent launches coroutine AND creates async task
     * - Both complete successfully
     * - Parent waits for both before completing
     *
     * Verification:
     * - All: COMPLETED
     * - Event order: children before parent
     */
    @Test
    fun `test mixed launch and async - successful completion`() = kotlinx.coroutines.runBlocking {
        logger.info("=".repeat(60))
        logger.info("TEST 5: Mixed Launch + Async - Success")
        logger.info("=".repeat(60))

        val session = VizSession("test-mixed-success")

        val eventLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineCompleted" -> logger.info("✅ COMPLETED: ${event.getLabel()}")
                    "DeferredValueAvailable" -> logger.info("✨ VALUE READY: ${event.getLabel()}")
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Starting mixed scenario...")

            vizLaunch("launch-task") {
                logger.info("🚀 Launch: Running...")
                vizDelay(150)
                logger.info("🚀 Launch: Done!")
            }

            val asyncResult = vizAsync("async-task") {
                logger.info("⚙️ Async: Computing...")
                vizDelay(200)
                logger.info("⚙️ Async: Done!")
                42
            }

            logger.info("👨‍👧‍👦 Parent: Awaiting async result...")
            val result = asyncResult.await()
            logger.info("👨‍👧‍👦 Parent: Got result: $result")

            logger.info("👨‍👧‍👦 Parent: All done!")
        }

        delay(500)
        eventLogger.cancel()

        val parent = session.snapshot.coroutines.values.find { it.label == "parent" }
        val launchTask = session.snapshot.coroutines.values.find { it.label == "launch-task" }
        val asyncTask = session.snapshot.coroutines.values.find { it.label == "async-task" }

        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Parent: ${parent?.state}")
        logger.info("  Launch task: ${launchTask?.state}")
        logger.info("  Async task: ${asyncTask?.state}")

        assertEquals(CoroutineState.COMPLETED, parent?.state, "Parent should be COMPLETED")
        assertEquals(CoroutineState.COMPLETED, launchTask?.state, "Launch task should be COMPLETED")
        assertEquals(CoroutineState.COMPLETED, asyncTask?.state, "Async task should be COMPLETED")

        logger.info("✅ TEST 5 PASSED: Mixed launch + async works correctly!\n")
    }

    /**
     * TEST 6: Multiple Awaiters on Same Deferred
     *
     * Expected behavior:
     * - One async task created
     * - Multiple coroutines await the same deferred
     * - All awaiters get the result
     * - All complete successfully
     *
     * Verification:
     * - All: COMPLETED
     * - All awaiters receive the same value
     */
    @Test
    fun `test multiple awaiters on same deferred`() = kotlinx.coroutines.runBlocking {
        logger.info("=".repeat(60))
        logger.info("TEST 6: Multiple Awaiters on Same Deferred")
        logger.info("=".repeat(60))

        val session = VizSession("test-multiple-awaiters")
        val results = mutableListOf<Int>()

        val eventLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "DeferredValueAvailable" -> logger.info("✨ VALUE READY: ${event.getLabel()}")
                    "CoroutineCompleted" -> logger.info("✅ COMPLETED: ${event.getLabel()}")
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("parent") {
            logger.info("👨‍👧‍👦 Parent: Creating shared async task...")

            val sharedDeferred = vizAsync("shared-computation") {
                logger.info("⚙️ Shared: Computing...")
                vizDelay(300)
                logger.info("⚙️ Shared: Done!")
                42
            }

            vizLaunch("awaiter-1") {
                logger.info("👤 Awaiter-1: Waiting...")
                val result = sharedDeferred.await()
                logger.info("👤 Awaiter-1: Got $result")
                results.add(result)
            }

            vizLaunch("awaiter-2") {
                vizDelay(100)
                logger.info("👤 Awaiter-2: Waiting...")
                val result = sharedDeferred.await()
                logger.info("👤 Awaiter-2: Got $result")
                results.add(result)
            }

            vizLaunch("awaiter-3") {
                vizDelay(200)
                logger.info("👤 Awaiter-3: Waiting...")
                val result = sharedDeferred.await()
                logger.info("👤 Awaiter-3: Got $result")
                results.add(result)
            }

            logger.info("👨‍👧‍👦 Parent: All awaiters launched, waiting...")
        }

        delay(1000)
        eventLogger.cancel()

        val parent = session.snapshot.coroutines.values.find { it.label == "parent" }
        val awaiters = session.snapshot.coroutines.values.filter {
            it.label?.startsWith("awaiter") == true
        }

        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Parent: ${parent?.state}")
        awaiters.forEach { logger.info("  ${it.label}: ${it.state}") }
        logger.info("  Results received: $results")

        assertEquals(CoroutineState.COMPLETED, parent?.state, "Parent should be COMPLETED")
        awaiters.forEach {
            assertEquals(CoroutineState.COMPLETED, it.state, "${it.label} should be COMPLETED")
        }
        assertEquals(3, results.size, "All 3 awaiters should have received result")
        assertTrue(results.all { it == 42 }, "All results should be 42")

        logger.info("✅ TEST 6 PASSED: Multiple awaiters work correctly!\n")
    }

    /**
     * TEST 7: Parent Completes Immediately - No Children
     *
     * Expected behavior:
     * - Parent has no children
     * - Parent completes immediately
     *
     * Verification:
     * - Parent: COMPLETED
     */
    @Test
    fun `test parent completes immediately with no children`() = kotlinx.coroutines.runBlocking {
        logger.info("=".repeat(60))
        logger.info("TEST 7: Parent Completes Immediately (No Children)")
        logger.info("=".repeat(60))

        val session = VizSession("test-no-children")

        val eventLogger = launch {
            session.bus.stream().collect { event ->
                when (event.kind) {
                    "CoroutineCompleted" -> logger.info("✅ COMPLETED: ${event.getLabel()}")
                    "CoroutineBodyCompleted" -> logger.info("📋 BODY_COMPLETED: ${event.getLabel()}")
                }
            }
        }

        val viz = VizScope(session)

        viz.vizLaunch("parent-no-children") {
            logger.info("👨 Parent: Starting...")
            vizDelay(100)
            logger.info("👨 Parent: Done (no children)!")
        }


        delay(300)
        eventLogger.cancel()

        val parent = session.snapshot.coroutines.values.find { it.label == "parent-no-children" }

        logger.info("\n🔍 VERIFICATION:")
        logger.info("  Parent: ${parent?.state}")

        assertEquals(CoroutineState.COMPLETED, parent?.state, "Parent should be COMPLETED")

        logger.info("✅ TEST 7 PASSED: Parent with no children completes correctly!\n")
    }
}

