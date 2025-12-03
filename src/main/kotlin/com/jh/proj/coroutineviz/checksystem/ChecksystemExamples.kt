package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * Comprehensive examples demonstrating how to use the checksystem validators.
 * 
 * Run with: ChecksystemExamples().runAllExamples()
 */
class ChecksystemExamples {

    private val logger = LoggerFactory.getLogger(ChecksystemExamples::class.java)

    /**
     * Example 1: Basic Event Recording & Sequence Checking
     */
    suspend fun exampleBasicSequenceCheck() = coroutineScope {
        logger.info("=" .repeat(60))
        logger.info("Example 1: Basic Sequence Check")
        logger.info("=".repeat(60))

        // 1. Setup
        val session = VizSession("example-basic")
        val recorder = EventRecorder()

        // 2. Start recording events
        val recordingJob = launch {
            session.bus.stream().collect { event ->
                recorder.record(event)
            }
        }

        // 3. Run scenario
        val scope = VizScope(session)
        scope.vizLaunch("worker") {
            logger.info("Worker started")
            vizDelay(100)
            logger.info("Worker completed")
        }.join()

        delay(100) // Let events settle

        // 4. Validate!
        val sequenceChecker = SequenceChecker(recorder)

        // Check lifecycle: Created -> Started -> Completed
        sequenceChecker.checkLifecycle("worker", SequenceChecker.Lifecycle.COMPLETE_SUCCESS)
            .assertSuccess()

        logger.info("✅ Example 1: Basic sequence check passed!")
        logger.info("Events recorded: ${recorder.all().size}")

        recordingJob.cancel()
    }

    /**
     * Example 2: Parent-Child Hierarchy Validation
     */
    suspend fun exampleHierarchyValidation() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 2: Hierarchy Validation")
        logger.info("=".repeat(60))

        val session = VizSession("example-hierarchy")
        val recorder = EventRecorder()

        val recordingJob = launch {
            session.bus.stream().collect { recorder.record(it) }
        }

        // Run scenario with nested coroutines
        val scope = VizScope(session)
        scope.vizLaunch("parent") {
            vizLaunch("child-1") {
                logger.info("child-1 running")
                vizDelay(50)
            }
            vizLaunch("child-2") {
                logger.info("child-2 running")
                vizDelay(100)
            }
            vizLaunch("child-3") {
                logger.info("child-3 running")
                vizLaunch("grandchild-1") {
                    logger.info("grandchild-1 running")
                    vizDelay(30)
                }
            }
        }.join()

        delay(200)

        // Validate hierarchy
        val hierarchyValidator = HierarchyValidator(session, recorder)

        // Check parent-child relationships
        hierarchyValidator.validateParentChild("parent", "child-1").assertSuccess()
        hierarchyValidator.validateParentChild("parent", "child-2").assertSuccess()
        hierarchyValidator.validateParentChild("child-3", "grandchild-1").assertSuccess()

        // Check siblings
        hierarchyValidator.validateSiblings("child-1", "child-2", "child-3").assertSuccess()

        // Check child count
        hierarchyValidator.validateChildCount("parent", 3).assertSuccess()

        // Check tree depth (parent=1, child=2, grandchild=3)
        hierarchyValidator.validateTreeDepth("grandchild-1", 3).assertSuccess()

        logger.info("✅ Example 2: Hierarchy validation passed!")

        recordingJob.cancel()
    }

    /**
     * Example 3: Structured Concurrency - Exception Propagation
     */
    suspend fun exampleExceptionPropagation() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 3: Exception Propagation")
        logger.info("=".repeat(60))

        val session = VizSession("example-exception")
        val recorder = EventRecorder()

        val recordingJob = launch {
            session.bus.stream().collect { recorder.record(it) }
        }

        val scope = VizScope(session)

        try {
            scope.vizLaunch("parent") {
                // Long-running child - should be cancelled
                vizLaunch("child-1-long") {
                    try {
                        logger.info("child-1-long starting long task...")
                        vizDelay(5000)
                        logger.info("child-1-long completed (should NOT see this)")
                    } catch (e: CancellationException) {
                        logger.warn("child-1-long was cancelled!")
                        throw e
                    }
                }

                vizDelay(50) // Ensure child-1 starts

                // Failing child
                vizLaunch("child-2-fails") {
                    logger.info("child-2-fails starting...")
                    vizDelay(100)
                    logger.error("child-2-fails throwing exception!")
                    throw RuntimeException("Intentional failure!")
                }
            }.join()
        } catch (e: Exception) {
            logger.info("Caught expected exception: ${e.message}")
        }

        delay(500)

        // === VALIDATION ===

        // Lifecycle checks
        val lifecycleValidator = LifecycleValidator(recorder)
        lifecycleValidator.hasTerminalState("child-2-fails", LifecycleValidator.State.FAILED)
            .assertSuccess()
        lifecycleValidator.hasTerminalState("parent", LifecycleValidator.State.CANCELLED)
            .assertSuccess()
        lifecycleValidator.hasTerminalState("child-1-long", LifecycleValidator.State.CANCELLED)
            .assertSuccess()

        // Structured concurrency validation
        val scValidator = StructuredConcurrencyValidator(session, recorder)
        scValidator.validateExceptionPropagation(
            failedChildLabel = "child-2-fails",
            parentLabel = "parent",
            siblingLabels = listOf("child-1-long")
        ).assertSuccess()

        // Event ordering: child fails -> parent cancelled -> sibling cancelled
        val happensBeforeChecker = HappensBeforeChecker(recorder)
        happensBeforeChecker.checkChain(
            EventSelector.labeled("child-2-fails", "CoroutineFailed"),
            EventSelector.labeled("parent", "CoroutineCancelled"),
            EventSelector.labeled("child-1-long", "CoroutineCancelled")
        ).assertSuccess()

        logger.info("✅ Example 3: Exception propagation validated!")

        recordingJob.cancel()
    }

    /**
     * Example 4: Async/Await Validation
     */
    suspend fun exampleAsyncAwait() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 4: Async/Await Validation")
        logger.info("=".repeat(60))

        val session = VizSession("example-async")
        val recorder = EventRecorder()

        val recordingJob = launch {
            session.bus.stream().collect { recorder.record(it) }
        }

        val scope = VizScope(session)

        scope.vizLaunch("parent") {
            logger.info("Starting async tasks...")

            val deferred1 = vizAsync("compute-A") {
                logger.info("compute-A running...")
                vizDelay(100)
                "Result A"
            }

            val deferred2 = vizAsync("compute-B") {
                logger.info("compute-B running...")
                vizDelay(150)
                42
            }

            val resultA = deferred1.await()
            val resultB = deferred2.await()

            logger.info("Results: $resultA, $resultB")
        }.join()

        delay(300)

        // Validate async lifecycles
        val deferredValidator = DeferredTrackingValidator(recorder)
        deferredValidator.validateAsyncLifecycle("compute-A").assertSuccess()
        deferredValidator.validateAsyncLifecycle("compute-B").assertSuccess()

        // Validate hierarchy
        val hierarchyValidator = HierarchyValidator(session, recorder)
        hierarchyValidator.validateParentChild("parent", "compute-A").assertSuccess()
        hierarchyValidator.validateParentChild("parent", "compute-B").assertSuccess()

        // Validate timing - compute-A should be faster
        val timingAnalyzer = TimingAnalyzer(recorder)
        timingAnalyzer.validateCompletedBefore("compute-A", "compute-B").assertSuccess()

        logger.info("✅ Example 4: Async/await validation passed!")

        recordingJob.cancel()
    }

    /**
     * Example 5: Suspension & Timing Validation
     */
    suspend fun exampleSuspensionTiming() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 5: Suspension & Timing")
        logger.info("=".repeat(60))

        val session = VizSession("example-suspension")
        val recorder = EventRecorder()

        val recordingJob = launch {
            session.bus.stream().collect { recorder.record(it) }
        }

        val scope = VizScope(session)

        scope.vizLaunch("worker") {
            logger.info("Worker starting with multiple delays...")
            vizDelay(100)  // Suspension 1
            logger.info("After delay 1")
            vizDelay(200)  // Suspension 2
            logger.info("After delay 2")
            vizDelay(50)   // Suspension 3
            logger.info("Worker done")
        }.join()

        delay(500)

        // Suspension validation
        val suspensionValidator = SuspensionValidator(recorder)
        suspensionValidator.validateWasSuspended("worker").assertSuccess()
        suspensionValidator.validateSuspensionReason("worker", "delay").assertSuccess()

        // Timing validation
        val timingAnalyzer = TimingAnalyzer(recorder)

        // Total should be ~350ms (100 + 200 + 50)
        timingAnalyzer.validateDuration("worker", 300, 600).assertSuccess()
        timingAnalyzer.validateMinDuration("worker", 300).assertSuccess()

        val stats = timingAnalyzer.getTimingStats("worker")
        logger.info("Timing stats: totalDuration=${stats?.totalDurationMs}ms, execution=${stats?.executionTimeMs}ms")

        logger.info("✅ Example 5: Suspension & timing validation passed!")

        recordingJob.cancel()
    }

    /**
     * Example 6: Using TestScenarioRunner (Recommended!)
     */
    suspend fun exampleTestScenarioRunner() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 6: TestScenarioRunner")
        logger.info("=".repeat(60))

        val runner = TestScenarioRunner()

        // Single scenario with validations
        val result = runner.runScenario(
            name = "parent-waits-for-children",
            timeout = 10_000,
            validations = listOf(
                // Lifecycle validations
                { ctx -> ctx.lifecycleValidator.validateCoroutineLifecycle("parent") },
                { ctx -> ctx.lifecycleValidator.validateCoroutineLifecycle("child-1") },
                { ctx -> ctx.lifecycleValidator.validateCoroutineLifecycle("child-2") },

                // Hierarchy validations
                { ctx -> ctx.hierarchyValidator.validateParentChild("parent", "child-1") },
                { ctx -> ctx.hierarchyValidator.validateParentChild("parent", "child-2") },
                { ctx -> ctx.hierarchyValidator.validateSiblings("child-1", "child-2") },

                // Structured concurrency
                { ctx ->
                    ctx.structuredConcurrencyValidator.validateParentWaitsForChildren(
                        "parent", listOf("child-1", "child-2")
                    )
                },

                // Timing
                { ctx -> ctx.timingAnalyzer.validateMaxDuration("parent", 1000) },

                // Suspension
                { ctx -> ctx.suspensionValidator.validateWasSuspended("child-1") },
                { ctx -> ctx.suspensionValidator.validateWasSuspended("child-2") }
            )
        ) {
            // This is the scenario block
            vizLaunch("parent") {
                vizLaunch("child-1") {
                    vizDelay(100)
                }
                vizLaunch("child-2") {
                    vizDelay(200)
                }
            }
        }

        // Print results
        result.printSummary()

        // Assert all passed
        result.assertAllPassed()

        logger.info("✅ Example 6: TestScenarioRunner completed!")
    }

    /**
     * Example 7: Running Multiple Scenarios
     */
    suspend fun exampleMultipleScenarios() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 7: Multiple Scenarios")
        logger.info("=".repeat(60))

        val runner = TestScenarioRunner()

        val results = runner.runAll(
            // Scenario 1: Simple launch
            TestScenarioRunner.scenario(
                name = "simple-launch",
                validations = listOf(
                    { ctx ->
                        ctx.sequenceChecker.checkLifecycle(
                            "worker",
                            SequenceChecker.Lifecycle.COMPLETE_SUCCESS
                        )
                    }
                )
            ) {
                vizLaunch("worker") { vizDelay(50) }
            },

            // Scenario 2: Nested hierarchy
            TestScenarioRunner.scenario(
                name = "nested-hierarchy",
                validations = listOf(
                    { ctx -> ctx.hierarchyValidator.validateParentChild("root", "level-1") },
                    { ctx -> ctx.hierarchyValidator.validateParentChild("level-1", "level-2") },
                    { ctx -> ctx.hierarchyValidator.validateTreeDepth("level-2", 3) }
                )
            ) {
                vizLaunch("root") {
                    vizLaunch("level-1") {
                        vizLaunch("level-2") { vizDelay(30) }
                    }
                }
            },

            // Scenario 3: Concurrent children
            TestScenarioRunner.scenario(
                name = "concurrent-children",
                validations = listOf(
                    { ctx -> ctx.hierarchyValidator.validateChildCount("parent", 3) },
                    { ctx ->
                        ctx.timingAnalyzer.validateConcurrentStart(
                            listOf("child-1", "child-2", "child-3"), toleranceMs = 100
                        )
                    }
                )
            ) {
                vizLaunch("parent") {
                    vizLaunch("child-1") { vizDelay(50) }
                    vizLaunch("child-2") { vizDelay(50) }
                    vizLaunch("child-3") { vizDelay(50) }
                }
            }
        )

        // Print aggregated results
        results.printSummary()

        // Assert all passed
        results.assertAllPassed()

        logger.info("✅ Example 7: Multiple scenarios completed!")
    }

    /**
     * Example 8: Happens-Before Chains
     */
    suspend fun exampleHappensBeforeChains() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 8: Happens-Before Chains")
        logger.info("=".repeat(60))

        val session = VizSession("example-happens-before")
        val recorder = EventRecorder()

        val recordingJob = launch {
            session.bus.stream().collect { recorder.record(it) }
        }

        val scope = VizScope(session)

        // Sequential execution
        scope.vizLaunch("first") {
            logger.info("first running")
            vizDelay(50)
        }.join()

        scope.vizLaunch("second") {
            logger.info("second running")
            vizDelay(50)
        }.join()

        scope.vizLaunch("third") {
            logger.info("third running")
            vizDelay(50)
        }.join()

        delay(200)

        val happensBeforeChecker = HappensBeforeChecker(recorder)

        // Chain validation: first completes -> second starts -> second completes -> third starts
        happensBeforeChecker.checkChain(
            EventSelector.labeled("first", "CoroutineCompleted"),
            EventSelector.labeled("second", "CoroutineStarted"),
            EventSelector.labeled("second", "CoroutineCompleted"),
            EventSelector.labeled("third", "CoroutineStarted")
        ).assertSuccess()

        // Simple happens-before
        happensBeforeChecker.checkHappensBefore(
            EventSelector.labeled("first", "CoroutineCreated"),
            EventSelector.labeled("third", "CoroutineCreated")
        ).assertSuccess()

        // Cross-coroutine order (shortcut)
        happensBeforeChecker.checkCrossCoroutineOrder(
            "first" to "CoroutineCompleted",
            "third" to "CoroutineStarted"
        ).assertSuccess()

        logger.info("✅ Example 8: Happens-before validation passed!")

        recordingJob.cancel()
    }

    /**
     * Example 9: Job State Validation
     */
    suspend fun exampleJobStateValidation() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 9: Job State Validation")
        logger.info("=".repeat(60))

        val session = VizSession("example-job-state")
        val recorder = EventRecorder()

        val recordingJob = launch {
            session.bus.stream().collect { recorder.record(it) }
        }

        val scope = VizScope(session)

        scope.vizLaunch("parent") {
            vizLaunch("child-1") {
                logger.info("child-1 running")
                vizDelay(100)
            }
            vizLaunch("child-2") {
                logger.info("child-2 running")
                vizDelay(150)
            }
        }.join()

        delay(300)

        val jobStateValidator = JobStateValidator(recorder)

        // Validate job was active
        jobStateValidator.validateJobWasActive("parent").assertSuccess()

        // Validate job completed (not cancelled)
        jobStateValidator.validateJobCompleted("parent").assertSuccess()

        // Validate state sequence
        jobStateValidator.validateStateSequence("parent").assertSuccess()

        logger.info("✅ Example 9: Job state validation passed!")

        recordingJob.cancel()
    }

    /**
     * Example 10: Complete Integration Test
     */
    suspend fun exampleCompleteIntegrationTest() = coroutineScope {
        logger.info("=".repeat(60))
        logger.info("Example 10: Complete Integration Test")
        logger.info("=".repeat(60))

        val runner = TestScenarioRunner()

        val result = runner.runScenario(
            name = "complete-integration-test",
            validations = listOf(
                // === Lifecycle ===
                { ctx ->
                    ctx.lifecycleValidator.validateAllLifecycles(
                        "parent", "fast-child", "slow-child", "async-task"
                    )
                },

                // === Hierarchy ===
                { ctx -> ctx.hierarchyValidator.validateChildCount("parent", 3) },
                { ctx -> ctx.hierarchyValidator.validateSiblings("fast-child", "slow-child", "async-task") },

                // === Structured Concurrency ===
                { ctx ->
                    ctx.structuredConcurrencyValidator.validateParentWaitsForChildren(
                        "parent", listOf("fast-child", "slow-child", "async-task")
                    )
                },

                // === Timing ===
                { ctx -> ctx.timingAnalyzer.validateCompletedBefore("fast-child", "slow-child") },
                { ctx -> ctx.timingAnalyzer.validateMaxDuration("parent", 1000) },

                // === Suspension ===
                { ctx -> ctx.suspensionValidator.validateWasSuspended("fast-child") },
                { ctx -> ctx.suspensionValidator.validateSuspensionReason("slow-child", "delay") },

                // === Event Order ===
                { ctx ->
                    ctx.happensBeforeChecker.checkHappensBefore(
                        EventSelector.labeled("fast-child", "CoroutineCompleted"),
                        EventSelector.labeled("slow-child", "CoroutineCompleted")
                    )
                },

                // === Sequence ===
                { ctx ->
                    ctx.sequenceChecker.checkLifecycle(
                        "parent",
                        SequenceChecker.Lifecycle.COMPLETE_SUCCESS
                    )
                }
            )
        ) {
            vizLaunch("parent") {
                vizLaunch("fast-child") {
                    vizDelay(50)
                }

                vizLaunch("slow-child") {
                    vizDelay(200)
                }

                val deferred = vizAsync("async-task") {
                    vizDelay(100)
                    "computed-value"
                }

                val asyncResult = deferred.await()
                logger.info("Async result: $asyncResult")
            }
        }

        // Print detailed summary
        result.printSummary()

        // Get event statistics
        logger.info("\nEvent Statistics:")
        logger.info(result.context.recorder.summary())

        // Assert all validations passed
        result.assertAllPassed()

        logger.info("\n🎉 Example 10: Complete integration test passed!")
    }

    /**
     * Run all examples
     */
    suspend fun runAllExamples() = coroutineScope {
        logger.info("#".repeat(70))
        logger.info("# CHECKSYSTEM EXAMPLES - Running All")
        logger.info("#".repeat(70))

        val examples = listOf(
            "Basic Sequence Check" to ::exampleBasicSequenceCheck,
            "Hierarchy Validation" to ::exampleHierarchyValidation,
            "Exception Propagation" to ::exampleExceptionPropagation,
            "Async/Await" to ::exampleAsyncAwait,
            "Suspension & Timing" to ::exampleSuspensionTiming,
            "TestScenarioRunner" to ::exampleTestScenarioRunner,
            "Multiple Scenarios" to ::exampleMultipleScenarios,
            "Happens-Before Chains" to ::exampleHappensBeforeChains,
            "Job State Validation" to ::exampleJobStateValidation,
            "Complete Integration" to ::exampleCompleteIntegrationTest
        )

        var passed = 0
        var failed = 0

        for ((name, example) in examples) {
            try {
                example()
                passed++
                delay(100)
            } catch (e: Exception) {
                logger.error("❌ FAILED: $name - ${e.message}")
                failed++
            }
        }

        logger.info("\n" + "#".repeat(70))
        logger.info("# RESULTS: $passed passed, $failed failed")
        logger.info("#".repeat(70))

        if (failed > 0) {
            throw AssertionError("$failed examples failed!")
        }

        logger.info("🎉 ALL EXAMPLES PASSED!")
    }

    companion object {
        /**
         * Main entry point to run all examples
         */
        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            ChecksystemExamples().runAllExamples()
        }
    }
}

