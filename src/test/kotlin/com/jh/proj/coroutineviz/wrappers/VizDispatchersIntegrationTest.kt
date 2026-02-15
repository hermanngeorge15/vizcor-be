package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.dispatcher.DispatcherSelected
import com.jh.proj.coroutineviz.events.dispatcher.ThreadAssigned
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import java.util.concurrent.Executors

@DisplayName("VizDispatchers Integration Tests")
class VizDispatchersIntegrationTest {

    @Test
    @DisplayName("Integration: Complete scenario with mixed dispatchers")
    fun testCompleteScenarioWithMixedDispatchers() = runTest {
        // Simulate a real-world scenario
        val session = VizSession("integration-mixed-dispatchers")
        val dispatchers = VizDispatchers(session, scopeId = "api-handlers")
        
        // Scope for CPU-intensive work
        val cpuScope = VizScope(session, context = dispatchers.default, scopeId = "cpu-scope")
        
        // Scope for I/O work
        val ioScope = VizScope(session, context = dispatchers.io, scopeId = "io-scope")
        
        // Simulate API handler
        val apiJob = cpuScope.vizLaunch(label = "api-handler") {
            // Parse request (CPU)
            vizDelay(50)
            
            // Read from database (I/O)
            ioScope.vizLaunch(label = "db-read") {
                vizDelay(100)
            }.join()
            
            // Process data (CPU)
            vizDelay(50)
            
            // Write to database (I/O)
            ioScope.vizLaunch(label = "db-write") {
                vizDelay(100)
            }.join()
        }
        
        apiJob.join()
        
        // Verify the complete flow was tracked
        val events = session.store.all()
        val dispatcherEvents = events.filter { it.kind == "DispatcherSelected" }
        val coroutineEvents = events.filter { it.kind == "CoroutineCreated" }
        
        // Should have tracked all 3 coroutines
        assertEquals(3, coroutineEvents.size, "Should have 3 coroutines")
        
        // Should have both Default and IO dispatchers
        val dispatcherSelectedEvents = dispatcherEvents.filterIsInstance<DispatcherSelected>()
        
        val hasDefault = dispatcherSelectedEvents.any { it.dispatcherName == "Dispatchers.Default" }
        val hasIO = dispatcherSelectedEvents.any { it.dispatcherName == "Dispatchers.IO" }
        
        assertTrue(hasDefault, "Should use Default dispatcher")
        assertTrue(hasIO, "Should use IO dispatcher")
        
        println("✅ Integration test passed!")
        println("   - Coroutines created: ${coroutineEvents.size}")
        println("   - Dispatcher events: ${dispatcherEvents.size}")
        println("   - Used Default: $hasDefault")
        println("   - Used IO: $hasIO")
    }

    @Test
    @DisplayName("Integration: Producer-Consumer pattern with different dispatchers")
    fun testProducerConsumerWithDispatchers() = runBlocking {
        val session = VizSession("integration-producer-consumer")
        val dispatchers = VizDispatchers(session, scopeId = "workers")
        
        val scope = VizScope(session, context = dispatchers.default)
        
        // Producer on Default (CPU-intensive data generation)
        val producerJob = scope.vizLaunch(label = "producer") {
            repeat(3) { i ->
                vizDelay(50) // Simulate computation
                println("Produced item $i")
            }
        }
        
        // Consumer on IO (I/O-intensive data storage)
        val consumerJob = scope.vizLaunch(label = "consumer", context = dispatchers.io) {
            repeat(3) { i ->
                vizDelay(100) // Simulate I/O
                println("Consumed item $i")
            }
        }
        
        producerJob.join()
        consumerJob.join()
        
        // Verify both dispatchers were used
        val events = session.store.all()
        val dispatcherEvents = events.filter { it.kind == "DispatcherSelected" }
        
        assertTrue(dispatcherEvents.size >= 2, "Should have dispatcher events for both coroutines")
        
        println("✅ Producer-Consumer test passed!")
        println("   - Total events: ${events.size}")
        println("   - Dispatcher selections: ${dispatcherEvents.size}")
    }

    @Test
    @DisplayName("Integration: Parallel tasks with vizAsync on different dispatchers")
    fun testParallelAsyncTasksWithDispatchers() = runBlocking {
        val session = VizSession("integration-parallel-async")
        val dispatchers = VizDispatchers(session, scopeId = "parallel")
        
        val scope = VizScope(session, context = dispatchers.default)
        
        // Launch 3 async tasks on IO dispatcher
        val tasks = List(3) { index ->
            scope.vizAsync(label = "task-$index", context = dispatchers.io) {
                vizDelay(100)
                "result-$index"
            }
        }
        
        // Wait for all and collect results
        val results = tasks.map { it.await() }
        
        assertEquals(listOf("result-0", "result-1", "result-2"), results)
        
        // Verify all tasks were tracked
        val events = session.store.all()
        val deferredEvents = events.filter { it.kind == "DeferredValueAvailable" }
        
        assertEquals(3, deferredEvents.size, "Should have 3 deferred value events")
        
        println("✅ Parallel async test passed!")
        println("   - Results: $results")
        println("   - Deferred events: ${deferredEvents.size}")
    }

    @Test
    @DisplayName("Integration: Custom dispatcher with thread pool")
    fun testCustomThreadPoolDispatcher() = runBlocking {
        val session = VizSession("integration-custom-pool")
        val dispatchers = VizDispatchers(session, scopeId = "custom")
        
        // Create custom thread pool
        val customPool = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "CustomWorker-${System.currentTimeMillis()}")
        }.asCoroutineDispatcher()
        
        try {
            // Instrument it
            val instrumentedCustom = dispatchers.instrument(customPool, "CustomThreadPool")
            
            val scope = VizScope(session, context = instrumentedCustom)
            
            // Launch multiple tasks on custom pool
            val jobs = List(5) { index ->
                scope.vizLaunch(label = "custom-task-$index") {
                    val threadName = Thread.currentThread().name
                    println("Task $index running on: $threadName")
                    vizDelay(50)
                }
            }
            
            jobs.forEach { it.join() }
            
            // Verify custom dispatcher was used
            val events = session.store.all()
            val dispatcherEvents = events.filter { it.kind == "DispatcherSelected" }
            val threadEvents = events.filter { it.kind == "ThreadAssigned" }
            
            assertTrue(dispatcherEvents.size >= 5, "Should have at least 5 dispatcher events, got ${dispatcherEvents.size}")
            
            val dispatcherSelectedEvents = dispatcherEvents.filterIsInstance<DispatcherSelected>()
            val hasCustom = dispatcherSelectedEvents.any { it.dispatcherName == "CustomThreadPool" }
            
            assertTrue(hasCustom, "Should use CustomThreadPool")
            
            // Verify threads were named correctly
            val threadAssignedEvents = threadEvents.filterIsInstance<ThreadAssigned>()
            val hasCustomThread = threadAssignedEvents.any { it.threadName.contains("CustomWorker") }
            
            assertTrue(hasCustomThread, "Should run on CustomWorker threads")
            
            println("✅ Custom thread pool test passed!")
            println("   - Dispatcher events: ${dispatcherEvents.size}")
            println("   - Thread events: ${threadEvents.size}")
            
        } finally {
            customPool.close()
        }
    }

    @Test
    @DisplayName("Integration: Deep nesting with dispatcher switches")
    fun testDeepNestingWithDispatcherSwitches() = runBlocking {
        val session = VizSession("integration-deep-nesting")
        val dispatchers = VizDispatchers(session, scopeId = "nested")
        
        val scope = VizScope(session, context = dispatchers.default)
        
        scope.vizLaunch(label = "level-0") {
            vizDelay(50)
            
            // Level 1: Switch to IO
            vizLaunch(label = "level-1", context = dispatchers.io) {
                vizDelay(50)
                
                // Level 2: Switch back to Default
                vizLaunch(label = "level-2", context = dispatchers.default) {
                    vizDelay(50)
                    
                    // Level 3: Switch to IO again
                    vizLaunch(label = "level-3", context = dispatchers.io) {
                        vizDelay(50)
                    }
                }
            }
        }.join()
        
        // Verify all levels were tracked
        val events = session.store.all()
        val dispatcherEvents = events.filterIsInstance<DispatcherSelected>()
        
        // Deep nesting with dispatcher switches should generate multiple dispatcher events
        assertTrue(dispatcherEvents.isNotEmpty(), "Should have dispatcher events for nested levels, got ${dispatcherEvents.size}")
        
        println("✅ Deep nesting test passed!")
        println("   - Dispatcher events: ${dispatcherEvents.size}")
        println("   - Total events: ${events.size}")
    }

    @Test
    @DisplayName("Integration: Exception handling with different dispatchers")
    fun testExceptionHandlingWithDispatchers() = runBlocking {
        val session = VizSession("integration-exception-handling")
        val dispatchers = VizDispatchers(session, scopeId = "exception")
        
        // Use SupervisorJob to allow observation without test failing
        val scope = VizScope(session, context = dispatchers.default + SupervisorJob())
        
        val job = scope.vizLaunch(label = "parent") {
            // Child 1: Succeeds on Default
            vizLaunch(label = "child-success", context = dispatchers.default) {
                vizDelay(100)
            }
            
            // Child 2: Fails on IO
            vizLaunch(label = "child-fail", context = dispatchers.io) {
                vizDelay(50)
                throw RuntimeException("Simulated failure")
            }
        }
        
        job.join()
        
        // Verify events were tracked despite exception
        val events = session.store.all()
        val dispatcherEvents = events.filterIsInstance<DispatcherSelected>()
        
        assertTrue(dispatcherEvents.isNotEmpty(), "Should have dispatcher events even with exceptions")
        
        println("✅ Exception handling test passed!")
        println("   - Dispatcher events: ${dispatcherEvents.size}")
        println("   - Total events: ${events.size}")
    }

    @Test
    @DisplayName("Integration: Stress test with many coroutines on multiple dispatchers")
    fun testStressTestWithManyCoroutines() = runBlocking {
        val session = VizSession("integration-stress-test")
        val dispatchers = VizDispatchers(session, scopeId = "stress")
        
        val scope = VizScope(session, context = dispatchers.default)
        
        val coroutineCount = 50
        val jobs = mutableListOf<Job>()
        
        // Launch many coroutines alternating between Default and IO
        repeat(coroutineCount) { index ->
            val dispatcher = if (index % 2 == 0) dispatchers.default else dispatchers.io
            val job = scope.vizLaunch(label = "stress-$index", context = dispatcher) {
                vizDelay(10)
            }
            jobs.add(job)
        }
        
        // Wait for all
        jobs.forEach { it.join() }
        
        // Verify all were tracked
        val events = session.store.all()
        val coroutineEvents = events.filter { it.kind == "CoroutineCreated" }
        val dispatcherEvents = events.filter { it.kind == "DispatcherSelected" }
        val completedEvents = events.filter { it.kind == "CoroutineCompleted" }
        
        assertEquals(coroutineCount, coroutineEvents.size, "Should create $coroutineCount coroutines")
        assertTrue(dispatcherEvents.size >= coroutineCount, "Should have at least $coroutineCount dispatcher events, got ${dispatcherEvents.size}")
        assertEquals(coroutineCount, completedEvents.size, "Should complete all coroutines")
        
        println("✅ Stress test passed!")
        println("   - Coroutines: ${coroutineEvents.size}")
        println("   - Dispatcher events: ${dispatcherEvents.size}")
        println("   - Completed: ${completedEvents.size}")
        println("   - Total events: ${events.size}")
    }
}

