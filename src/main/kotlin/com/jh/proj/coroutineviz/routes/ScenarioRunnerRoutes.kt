package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.scenarios.ScenarioConfigRequest
import com.jh.proj.coroutineviz.scenarios.ScenarioExecutionResponse
import com.jh.proj.coroutineviz.scenarios.ScenarioRunner
import com.jh.proj.coroutineviz.scenarios.toScenarioConfig
import com.jh.proj.coroutineviz.session.SessionManager
import com.jh.proj.coroutineviz.session.VizSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CoroutineVizRouting")

fun Route.registerScenarioRunnerRoutes() {
    post("/api/scenarios/nested") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running nested coroutines scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runNestedCoroutines(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Scenario completed. Connect to /api/sessions/${session.sessionId}/stream for live events.",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/parallel") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running parallel execution scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runParallelExecution(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Parallel scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/cancellation") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running cancellation scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runCancellationScenario(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Cancellation scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/deep-nesting") {
        val sessionId = call.request.queryParameters["sessionId"]
        val depth = call.request.queryParameters["depth"]?.toIntOrNull() ?: 5
        val session = getOrCreateSession(sessionId)

        logger.info("Running deep nesting scenario (depth=$depth) in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runDeepNesting(session, depth)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Deep nesting scenario completed (depth=$depth)",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/mixed") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running mixed scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runMixedScenario(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Mixed scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/exception") {
        val sessionId = call.request.queryParameters["sessionId"]
        val session = getOrCreateSession(sessionId)

        logger.info("Running exception scenario in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runExceptionScenario(session)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Exception scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    // ============================================================================
    // REALISTIC SCENARIOS - Real-world service simulations
    // ============================================================================

    post("/api/scenarios/order-processing") {
        val sessionId = call.request.queryParameters["sessionId"]
        val shouldFail = call.request.queryParameters["fail"]?.toBoolean() ?: false
        val session = getOrCreateSession(sessionId)

        logger.info("Running Order Processing scenario (fail=$shouldFail) in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runOrderProcessingScenario(session, shouldFail)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Order Processing scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/user-registration") {
        val sessionId = call.request.queryParameters["sessionId"]
        val shouldFailEmail = call.request.queryParameters["failEmail"]?.toBoolean() ?: false
        val session = getOrCreateSession(sessionId)

        logger.info("Running User Registration scenario (failEmail=$shouldFailEmail) in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runUserRegistrationScenario(session, shouldFailEmail)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "User Registration scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/report-generation") {
        val sessionId = call.request.queryParameters["sessionId"]
        val shouldTimeout = call.request.queryParameters["timeout"]?.toBoolean() ?: false
        val session = getOrCreateSession(sessionId)

        logger.info("Running Report Generation scenario (timeout=$shouldTimeout) in session: ${session.sessionId}")

        call.runScenarioWithResponse(session) {
            ScenarioRunner.runReportGenerationScenario(session, shouldTimeout)
            ScenarioCompletionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Report Generation scenario completed",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )
        }
    }

    post("/api/scenarios/custom") {
        logger.info("Received custom scenario configuration request")

        try {
            val request = call.receive<ScenarioConfigRequest>()

            logger.info("Custom scenario: ${request.name}")
            logger.debug("Description: ${request.description}")

            val session = getOrCreateSession(request.sessionId)
            logger.info("Using session: ${session.sessionId}")

            val scenarioConfig = request.toScenarioConfig()
            ScenarioRunner.runCustomScenario(session, scenarioConfig)

            delay(100)

            val response = ScenarioExecutionResponse(
                success = true,
                sessionId = session.sessionId,
                message = "Custom scenario '${request.name}' completed successfully",
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size
            )

            logger.info(
                "✅ Custom scenario completed successfully | " +
                    "Coroutines: ${response.coroutineCount} | Events: ${response.eventCount}"
            )

            call.respond(HttpStatusCode.OK, response)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid scenario configuration", e)
            call.respond(
                HttpStatusCode.BadRequest,
                ScenarioExecutionResponse(
                    success = false,
                    sessionId = "",
                    message = "Invalid scenario configuration: ${e.message}",
                    errors = listOf(e.message ?: "Unknown validation error")
                )
            )
        } catch (e: Exception) {
            logger.error("Error running custom scenario", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ScenarioExecutionResponse(
                    success = false,
                    sessionId = "",
                    message = "Error executing scenario: ${e.message}",
                    errors = listOf(e.stackTraceToString())
                )
            )
        }
    }

    get("/api/scenarios") {
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "scenarios" to listOf(
                    // ========== REALISTIC SCENARIOS (Featured) ==========
                    mapOf(
                        "id" to "order-processing",
                        "name" to "🛒 Order Processing",
                        "description" to "E-commerce flow: validation → inventory check → payment → database → notifications (parallel). Use ?fail=true to simulate payment failure.",
                        "endpoint" to "/api/scenarios/order-processing",
                        "category" to "realistic",
                        "duration" to "~15-20 seconds"
                    ),
                    mapOf(
                        "id" to "user-registration",
                        "name" to "👤 User Registration",
                        "description" to "Complete registration: validation → DB check → create user → parallel setup (profile, settings, avatar) → notifications. Use ?failEmail=true to simulate email retry.",
                        "endpoint" to "/api/scenarios/user-registration",
                        "category" to "realistic",
                        "duration" to "~18-25 seconds"
                    ),
                    mapOf(
                        "id" to "report-generation",
                        "name" to "📊 Report Generation",
                        "description" to "Data pipeline: parallel API fetches → data aggregation → PDF generation → parallel delivery (S3, email, Slack). Use ?timeout=true to simulate API timeout.",
                        "endpoint" to "/api/scenarios/report-generation",
                        "category" to "realistic",
                        "duration" to "~25-35 seconds"
                    ),
                    // ========== BASIC SCENARIOS ==========
                    mapOf(
                        "id" to "nested",
                        "name" to "Nested Coroutines",
                        "description" to "Demonstrates parent-child relationships and structured concurrency",
                        "endpoint" to "/api/scenarios/nested",
                        "category" to "basic"
                    ),
                    mapOf(
                        "id" to "parallel",
                        "name" to "Parallel Execution",
                        "description" to "Multiple coroutines running in parallel",
                        "endpoint" to "/api/scenarios/parallel",
                        "category" to "basic"
                    ),
                    mapOf(
                        "id" to "cancellation",
                        "name" to "Cancellation",
                        "description" to "Demonstrates coroutine cancellation and cleanup",
                        "endpoint" to "/api/scenarios/cancellation",
                        "category" to "basic"
                    ),
                    mapOf(
                        "id" to "deep-nesting",
                        "name" to "Deep Nesting",
                        "description" to "Deep hierarchy of nested coroutines (configurable depth)",
                        "endpoint" to "/api/scenarios/deep-nesting?depth=5",
                        "category" to "basic"
                    ),
                    mapOf(
                        "id" to "mixed",
                        "name" to "Mixed Sequential/Parallel",
                        "description" to "Combination of sequential and parallel execution",
                        "endpoint" to "/api/scenarios/mixed",
                        "category" to "basic"
                    ),
                    mapOf(
                        "id" to "exception",
                        "name" to "Exception Handling",
                        "description" to "Demonstrates exception tracking and failure states",
                        "endpoint" to "/api/scenarios/exception",
                        "category" to "basic"
                    )
                )
            )
        )
    }
}

private suspend fun ApplicationCall.runScenarioWithResponse(
    session: VizSession,
    block: suspend () -> ScenarioCompletionResponse
) {
    try {
        val response = block()
        delay(100)
        respond(HttpStatusCode.OK, response)
    } catch (e: Exception) {
        logger.error("Error running scenario", e)
        respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "Scenario failed: ${e.message}")
        )
    }
}

private suspend fun getOrCreateSession(sessionId: String?): VizSession {
    return sessionId?.let { SessionManager.getSession(it) }
        ?: SessionManager.createSession("auto-${System.currentTimeMillis()}")
}

