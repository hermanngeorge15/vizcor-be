package com.jh.proj.checksystem

import com.jh.proj.coroutineviz.checksystem.*
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests demonstrating the checksystem validators.
 * Run with: ./gradlew test --tests "com.jh.proj.checksystem.ChecksystemExamplesTest"
 */
class ChecksystemExamplesTest {

    /**
     * Helper to setup recording with proper timing.
     */
    private suspend fun CoroutineScope.setupRecording(session: VizSession): Pair<EventRecorder, Job> {
        val recorder = EventRecorder()
        val recordingJob = launch {
            session.bus.stream().collect { recorder.record(it) }
        }
        // Critical: yield + small delay to ensure recording job is ready
        yield()
        delay(10)
        return recorder to recordingJob
    }

    @Test
    fun `Example 1 - Basic sequence check`() = runTest {
        val session = VizSession("test-basic")
        val (recorder, recordingJob) = setupRecording(session)

        val scope = VizScope(session)
        scope.vizLaunch("worker") {
            vizDelay(50)
        }.join()

        delay(100)

        // Check that events were recorded
        val events = recorder.forLabel("worker")
        println("Events for 'worker': ${events.map { it.kind }}")
        assertTrue(events.isNotEmpty(), "Events should be recorded for 'worker'")

        // Check for key lifecycle events
        val sequenceChecker = SequenceChecker(recorder)
        sequenceChecker.checkContainsSequence(
            "worker",
            listOf("CoroutineCreated", "CoroutineStarted")
        ).assertSuccess()

        recordingJob.cancel()
    }

    @Test
    fun `Example 2 - Hierarchy validation`() = runTest {
        val session = VizSession("test-hierarchy")
        val (recorder, recordingJob) = setupRecording(session)

        val scope = VizScope(session)
        scope.vizLaunch("parent") {
            vizLaunch("child-1") { vizDelay(30) }
            vizLaunch("child-2") { vizDelay(50) }
        }.join()

        delay(100)

        val hierarchyValidator = HierarchyValidator(session, recorder)
        hierarchyValidator.validateParentChild("parent", "child-1").assertSuccess()
        hierarchyValidator.validateParentChild("parent", "child-2").assertSuccess()
        hierarchyValidator.validateSiblings("child-1", "child-2").assertSuccess()
        hierarchyValidator.validateChildCount("parent", 2).assertSuccess()

        recordingJob.cancel()
    }

    @Test
    fun `Example 3 - Lifecycle states`() = runTest {
        // Simplified test focusing on lifecycle validation
        val session = VizSession("test-lifecycle")
        val (recorder, recordingJob) = setupRecording(session)

        val scope = VizScope(session)
        scope.vizLaunch("parent") {
            vizLaunch("child-1") { vizDelay(50) }
            vizLaunch("child-2") { vizDelay(100) }
        }.join()

        delay(200)

        val lifecycleValidator = LifecycleValidator(recorder)
        
        // All coroutines should complete successfully
        lifecycleValidator.hasTerminalState("parent", LifecycleValidator.State.COMPLETED)
            .assertSuccess()
        lifecycleValidator.hasTerminalState("child-1", LifecycleValidator.State.COMPLETED)
            .assertSuccess()
        lifecycleValidator.hasTerminalState("child-2", LifecycleValidator.State.COMPLETED)
            .assertSuccess()

        recordingJob.cancel()
    }

    @Test
    fun `Example 4 - Async await validation`() = runTest {
        val session = VizSession("test-async")
        val (recorder, recordingJob) = setupRecording(session)

        val scope = VizScope(session)

        scope.vizLaunch("parent") {
            val deferred1 = vizAsync("compute-A") {
                vizDelay(50)
                "Result A"
            }
            val deferred2 = vizAsync("compute-B") {
                vizDelay(100)
                42
            }
            val resultA = deferred1.await()
            val resultB = deferred2.await()
            assertEquals("Result A", resultA)
            assertEquals(42, resultB)
        }.join()

        delay(200)

        val deferredValidator = DeferredTrackingValidator(recorder)
        deferredValidator.validateAsyncLifecycle("compute-A").assertSuccess()
        deferredValidator.validateAsyncLifecycle("compute-B").assertSuccess()

        val timingAnalyzer = TimingAnalyzer(recorder)
        timingAnalyzer.validateCompletedBefore("compute-A", "compute-B").assertSuccess()

        recordingJob.cancel()
    }

    @Test
    fun `Example 5 - Suspension validation`() = runTest {
        val session = VizSession("test-suspension")
        val (recorder, recordingJob) = setupRecording(session)

        val scope = VizScope(session)

        scope.vizLaunch("worker") {
            vizDelay(50)
            vizDelay(100)
        }.join()

        delay(200)

        val suspensionValidator = SuspensionValidator(recorder)
        suspensionValidator.validateWasSuspended("worker").assertSuccess()
        suspensionValidator.validateSuspensionReason("worker", "delay").assertSuccess()

        recordingJob.cancel()
    }

    @Test
    fun `Example 6 - Happens before chains`() = runTest {
        val session = VizSession("test-happens-before")
        val (recorder, recordingJob) = setupRecording(session)

        val scope = VizScope(session)

        // Sequential execution - each waits for previous to complete
        scope.vizLaunch("first") { vizDelay(30) }.join()
        scope.vizLaunch("second") { vizDelay(30) }.join()
        scope.vizLaunch("third") { vizDelay(30) }.join()

        delay(150)

        val happensBeforeChecker = HappensBeforeChecker(recorder)

        // Check that first completes before second starts
        happensBeforeChecker.checkHappensBefore(
            EventSelector.labeled("first", "CoroutineCompleted"),
            EventSelector.labeled("second", "CoroutineCreated")
        ).assertSuccess()

        // Check that second completes before third starts
        happensBeforeChecker.checkHappensBefore(
            EventSelector.labeled("second", "CoroutineCompleted"),
            EventSelector.labeled("third", "CoroutineCreated")
        ).assertSuccess()

        recordingJob.cancel()
    }

    @Test
    fun `Example 7 - TestScenarioRunner basic`() = runTest {
        val runner = TestScenarioRunner()

        val result = runner.runScenario(
            name = "simple-test",
            validations = listOf(
                // Check events were recorded
                { ctx ->
                    val events = ctx.recorder.forLabel("worker")
                    if (events.isEmpty()) {
                        OrderResult.Failure("No events recorded for 'worker'", 
                            context = mapOf("allEvents" to ctx.recorder.all().size))
                    } else {
                        OrderResult.Success
                    }
                },
                // Check that Created and Started events exist
                { ctx -> 
                    val events = ctx.recorder.forLabel("worker")
                    val hasCreated = events.any { it.kind == "CoroutineCreated" }
                    val hasStarted = events.any { it.kind == "CoroutineStarted" }
                    if (hasCreated && hasStarted) OrderResult.Success
                    else OrderResult.Failure("Worker lifecycle incomplete", 
                        context = mapOf("events" to events.map { it.kind }))
                }
            )
        ) {
            vizLaunch("worker") { vizDelay(50) }.join()
        }

        result.printSummary()
        
        if (!result.allPassed()) {
            println("Debug - All events: ${result.context.recorder.all().map { it.kind }}")
            println("Debug - Events by label: ${result.context.recorder.stats()}")
        }
        
        assertTrue(result.allPassed(), 
            "All validations should pass: ${result.validationResults.filter { !it.passed }.map { it.result.getFailureMessage() }}")
    }

    @Test
    fun `Example 8 - TestScenarioRunner multiple scenarios`() = runTest {
        val runner = TestScenarioRunner()

        val results = runner.runAll(
            TestScenarioRunner.scenario(
                name = "scenario-1",
                validations = listOf(
                    { ctx ->
                        val events = ctx.recorder.forLabel("task-1")
                        if (events.isNotEmpty()) OrderResult.Success
                        else OrderResult.Failure("No events for task-1")
                    }
                )
            ) {
                vizLaunch("task-1") { vizDelay(30) }.join()
            },

            TestScenarioRunner.scenario(
                name = "scenario-2",
                validations = listOf(
                    { ctx -> ctx.hierarchyValidator.validateParentChild("root", "child") }
                )
            ) {
                vizLaunch("root") {
                    vizLaunch("child") { vizDelay(30) }
                }.join()
            }
        )

        results.printSummary()
        assertTrue(results.allPassed(), "All scenarios should pass")
        assertEquals(2, results.totalScenarios)
    }

    @Test
    fun `Example 9 - Timing analyzer`() = runTest {
        val session = VizSession("test-timing")
        val (recorder, recordingJob) = setupRecording(session)

        val scope = VizScope(session)

        scope.vizLaunch("parent") {
            vizLaunch("fast") { vizDelay(50) }
            vizLaunch("slow") { vizDelay(150) }
        }.join()

        delay(250)

        val timingAnalyzer = TimingAnalyzer(recorder)

        // Fast completes before slow
        timingAnalyzer.validateCompletedBefore("fast", "slow").assertSuccess()

        // Get stats
        val fastStats = timingAnalyzer.getTimingStats("fast")
        val slowStats = timingAnalyzer.getTimingStats("slow")

        assertTrue(fastStats != null, "Should have timing stats for fast")
        assertTrue(slowStats != null, "Should have timing stats for slow")

        recordingJob.cancel()
    }

    @Test
    fun `Example 10 - Complete integration test`() = runTest {
        val runner = TestScenarioRunner()

        val result = runner.runScenario(
            name = "integration-test",
            validations = listOf(
                // Check events were recorded
                { ctx ->
                    val parentEvents = ctx.recorder.forLabel("parent")
                    if (parentEvents.isEmpty()) OrderResult.Failure("No events for parent")
                    else OrderResult.Success
                },

                // Hierarchy
                { ctx -> ctx.hierarchyValidator.validateParentChild("parent", "worker-1") },
                { ctx -> ctx.hierarchyValidator.validateParentChild("parent", "worker-2") },
                { ctx -> ctx.hierarchyValidator.validateChildCount("parent", 2) },

                // Check Created events exist (reliable in test environment)
                { ctx -> 
                    val events = ctx.recorder.forLabel("worker-1")
                    if (events.any { it.kind == "CoroutineCreated" }) OrderResult.Success
                    else OrderResult.Failure("worker-1 was not created")
                },
                { ctx -> 
                    val events = ctx.recorder.forLabel("worker-2")
                    if (events.any { it.kind == "CoroutineCreated" }) OrderResult.Success
                    else OrderResult.Failure("worker-2 was not created")
                }
            )
        ) {
            vizLaunch("parent") {
                vizLaunch("worker-1") { vizDelay(50) }
                vizLaunch("worker-2") { vizDelay(100) }
            }.join()
        }

        result.printSummary()
        result.assertAllPassed()
    }
}
