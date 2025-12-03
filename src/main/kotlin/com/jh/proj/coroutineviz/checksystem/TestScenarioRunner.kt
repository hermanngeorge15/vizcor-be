package com.jh.proj.coroutineviz.checksystem

import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * Automated test scenario runner for VizScope validation.
 *
 * Provides:
 * - Automated setup and teardown
 * - Event recording
 * - Validation execution
 * - Result aggregation
 *
 * Usage:
 * ```
 * val runner = TestScenarioRunner()
 * val result = runner.runScenario("my-test") { scope ->
 *     scope.vizLaunch("parent") {
 *         vizLaunch("child") { vizDelay(100) }
 *     }
 * }
 * result.assertAllPassed()
 * ```
 */
class TestScenarioRunner {

    private val logger = LoggerFactory.getLogger(TestScenarioRunner::class.java)

    /**
     * Runs a test scenario with automatic setup and validation.
     */
    suspend fun runScenario(
        name: String,
        timeout: Long = 30_000,
        validations: List<(TestContext) -> OrderResult> = emptyList(),
        scenario: suspend VizScope.() -> Unit
    ): TestResult = coroutineScope {
        logger.info("🧪 Running scenario: $name")

        val session = VizSession("test-$name")
        val recorder = EventRecorder()

        // Start event recording
        val recordingJob = launch {
            session.bus.stream().collect { event ->
                recorder.record(event)
            }
        }
        
        // Critical: ensure recording job is subscribed before scenario starts
        // yield() alone isn't enough - we need the collector to be active
        yield()
        delay(10) // Small delay to ensure Flow collector is ready

        val startTime = System.nanoTime()
        var scenarioError: Throwable? = null

        try {
            // Run scenario with timeout
            withTimeout(timeout) {
                val scope = VizScope(session)
                scope.scenario()
            }

            // Wait for events to settle
            delay(100)

        } catch (e: TimeoutCancellationException) {
            scenarioError = e
            logger.error("⏰ Scenario timed out: $name")
        } catch (e: Exception) {
            // Some scenarios intentionally throw exceptions
            scenarioError = e
            logger.warn("⚠️ Scenario threw exception: ${e.message}")
        }

        // Wait longer for completion events to be processed
        delay(200)

        val executionTime = (System.nanoTime() - startTime) / 1_000_000

        // Stop recording
        recordingJob.cancel()

        // Create test context for validations
        val context = TestContext(
            session = session,
            recorder = recorder,
            scenarioError = scenarioError
        )

        // Run validations
        val validationResults = validations.mapIndexed { index, validation ->
            try {
                val result = validation(context)
                ValidationResult(
                    index = index,
                    passed = result.isSuccess(),
                    result = result
                )
            } catch (e: Exception) {
                ValidationResult(
                    index = index,
                    passed = false,
                    result = OrderResult.Failure(
                        message = "Validation threw exception: ${e.message}",
                        context = mapOf("exception" to e.toString())
                    )
                )
            }
        }

        val passedCount = validationResults.count { it.passed }
        val failedCount = validationResults.count { !it.passed }

        logger.info("📊 Scenario '$name' completed:")
        logger.info("   - Execution time: ${executionTime}ms")
        logger.info("   - Events recorded: ${recorder.all().size}")
        logger.info("   - Validations: $passedCount passed, $failedCount failed")

        TestResult(
            scenarioName = name,
            executionTimeMs = executionTime,
            eventCount = recorder.all().size,
            scenarioError = scenarioError,
            validationResults = validationResults,
            context = context
        )
    }

    /**
     * Runs multiple scenarios and aggregates results.
     */
    suspend fun runAll(vararg scenarios: Scenario): AggregatedResults = coroutineScope {
        val results = scenarios.map { scenario ->
            runScenario(
                name = scenario.name,
                timeout = scenario.timeout,
                validations = scenario.validations,
                scenario = scenario.block
            )
        }

        AggregatedResults(
            totalScenarios = results.size,
            passedScenarios = results.count { it.allPassed() },
            failedScenarios = results.count { !it.allPassed() },
            totalValidations = results.sumOf { it.validationResults.size },
            passedValidations = results.sumOf { it.validationResults.count { v -> v.passed } },
            failedValidations = results.sumOf { it.validationResults.count { v -> !v.passed } },
            results = results
        )
    }

    /**
     * Test context passed to validation functions.
     */
    data class TestContext(
        val session: VizSession,
        val recorder: EventRecorder,
        val scenarioError: Throwable?
    ) {
        // Convenience accessors for validators
        val sequenceChecker = SequenceChecker(recorder)
        val happensBeforeChecker = HappensBeforeChecker(recorder)
        val lifecycleValidator = LifecycleValidator(recorder)
        val structuredConcurrencyValidator = StructuredConcurrencyValidator(session, recorder)
        val deferredTrackingValidator = DeferredTrackingValidator(recorder)
        val hierarchyValidator = HierarchyValidator(session, recorder)
        val jobStateValidator = JobStateValidator(recorder)
        val dispatcherValidator = DispatcherValidator(recorder)
        val suspensionValidator = SuspensionValidator(recorder)
        val timingAnalyzer = TimingAnalyzer(recorder)
    }

    /**
     * Result of a single validation.
     */
    data class ValidationResult(
        val index: Int,
        val passed: Boolean,
        val result: OrderResult
    )

    /**
     * Result of running a test scenario.
     */
    data class TestResult(
        val scenarioName: String,
        val executionTimeMs: Long,
        val eventCount: Int,
        val scenarioError: Throwable?,
        val validationResults: List<ValidationResult>,
        val context: TestContext
    ) {
        fun allPassed(): Boolean = validationResults.all { it.passed }

        fun assertAllPassed() {
            val failures = validationResults.filter { !it.passed }
            if (failures.isNotEmpty()) {
                val message = buildString {
                    appendLine("Scenario '$scenarioName' failed ${failures.size} validation(s):")
                    failures.forEach { failure ->
                        appendLine("  [${failure.index}] ${failure.result.getDetailedFailureMessage()}")
                    }
                }
                throw AssertionError(message)
            }
        }

        fun printSummary() {
            println("=".repeat(60))
            println("Scenario: $scenarioName")
            println("=".repeat(60))
            println("Execution time: ${executionTimeMs}ms")
            println("Events recorded: $eventCount")
            if (scenarioError != null) {
                println("Scenario error: ${scenarioError.message}")
            }
            println("Validations: ${validationResults.count { it.passed }}/${validationResults.size} passed")
            validationResults.forEachIndexed { index, result ->
                val icon = if (result.passed) "✅" else "❌"
                println("  $icon [$index] ${if (result.passed) "PASSED" else result.result.getFailureMessage()}")
            }
            println("=".repeat(60))
        }
    }

    /**
     * Scenario definition for batch running.
     */
    data class Scenario(
        val name: String,
        val timeout: Long = 30_000,
        val validations: List<(TestContext) -> OrderResult> = emptyList(),
        val block: suspend VizScope.() -> Unit
    )

    /**
     * Aggregated results from running multiple scenarios.
     */
    data class AggregatedResults(
        val totalScenarios: Int,
        val passedScenarios: Int,
        val failedScenarios: Int,
        val totalValidations: Int,
        val passedValidations: Int,
        val failedValidations: Int,
        val results: List<TestResult>
    ) {
        fun allPassed(): Boolean = failedScenarios == 0

        fun assertAllPassed() {
            if (!allPassed()) {
                val failedNames = results.filter { !it.allPassed() }.map { it.scenarioName }
                throw AssertionError("${failedScenarios}/${totalScenarios} scenarios failed: $failedNames")
            }
        }

        fun printSummary() {
            println("=".repeat(70))
            println("AGGREGATED TEST RESULTS")
            println("=".repeat(70))
            println("Scenarios: $passedScenarios/$totalScenarios passed")
            println("Validations: $passedValidations/$totalValidations passed")
            println()

            results.forEach { result ->
                val icon = if (result.allPassed()) "✅" else "❌"
                println("$icon ${result.scenarioName}: ${result.validationResults.count { it.passed }}/${result.validationResults.size} validations")
            }

            println("=".repeat(70))
            if (allPassed()) {
                println("🎉 ALL TESTS PASSED!")
            } else {
                println("⚠️ SOME TESTS FAILED!")
            }
            println("=".repeat(70))
        }
    }

    companion object {
        /**
         * DSL builder for creating scenarios.
         */
        fun scenario(
            name: String,
            timeout: Long = 30_000,
            validations: List<(TestContext) -> OrderResult> = emptyList(),
            block: suspend VizScope.() -> Unit
        ) = Scenario(name, timeout, validations, block)
    }
}

