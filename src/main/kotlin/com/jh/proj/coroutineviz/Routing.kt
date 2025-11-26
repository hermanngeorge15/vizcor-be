package com.jh.proj.coroutineviz

import com.jh.proj.coroutineviz.events.CoroutineCompleted
import com.jh.proj.coroutineviz.events.CoroutineCreated
import com.jh.proj.coroutineviz.events.CoroutineStarted
import com.jh.proj.coroutineviz.session.VizEventMain
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.session.SessionManager
import com.jh.proj.coroutineviz.scenarios.ScenarioRunner
import com.jh.proj.coroutineviz.scenarios.toScenarioConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CoroutineVizRouting")

fun Application.configureRouting() {
    install(SSE)
    routing {
        get("/") {
            logger.info("Received request to root endpoint")
            call.respondText("Hello World!")
        }

        sse("/hello") {
            logger.info("SSE connection established at /hello")
            send(ServerSentEvent("world"))
        }

        // Run VizEventMain scenario and return results
        get("/api/viz/nested-launch-scenario") {
            logger.info("┌─────────────────────────────────────────┐")
            logger.info("│  Starting VizEventMain Scenario        │")
            logger.info("└─────────────────────────────────────────┘")

            try {
                val vizMain = VizEventMain()

                logger.debug("VizEventMain instance created, executing main()")

                // Run the scenario
                vizMain.exampleOfNestedLaunches()

                logger.info("✅ VizEventMain scenario completed successfully")

                // Return success response
                call.respond(
                    HttpStatusCode.OK,
                    ScenarioResponse(
                        success = true,
                        message = "Scenario executed successfully. Check server logs for output."
                    )
                )
            } catch (e: Exception) {
                logger.error("❌ Error executing VizEventMain scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ScenarioResponse(
                        success = false,
                        message = "Error executing scenario: ${e.message}"
                    )
                )
            }
        }

        get("/api/viz/one-launch-scenario") {
            logger.info("┌─────────────────────────────────────────┐")
            logger.info("│  Starting VizEventMain Scenario        │")
            logger.info("└─────────────────────────────────────────┘")

            try {
                val vizMain = VizEventMain()

                logger.debug("VizEventMain instance created, executing main()")

                // Run the scenario
                vizMain.exampleSimpleLaunchVisualization()

                logger.info("✅ VizEventMain scenario completed successfully")

                // Return success response
                call.respond(
                    HttpStatusCode.OK,
                    ScenarioResponse(
                        success = true,
                        message = "Scenario executed successfully. Check server logs for output."
                    )
                )
            } catch (e: Exception) {
                logger.error("❌ Error executing VizEventMain scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ScenarioResponse(
                        success = false,
                        message = "Error executing scenario: ${e.message}"
                    )
                )
            }
        }

        get("/api/viz/two-launch-scenario") {
            logger.info("┌─────────────────────────────────────────┐")
            logger.info("│  Starting VizEventMain Scenario        │")
            logger.info("└─────────────────────────────────────────┘")

            try {
                val vizMain = VizEventMain()

                logger.debug("VizEventMain instance created, executing main()")

                // Run the scenario
                vizMain.exampleOfTwoLanuches()

                logger.info("✅ VizEventMain scenario completed successfully")

                // Return success response
                call.respond(
                    HttpStatusCode.OK,
                    ScenarioResponse(
                        success = true,
                        message = "Scenario executed successfully. Check server logs for output."
                    )
                )
            } catch (e: Exception) {
                logger.error("❌ Error executing VizEventMain scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ScenarioResponse(
                        success = false,
                        message = "Error executing scenario: ${e.message}"
                    )
                )
            }
        }
        
        // ========================================
        // TEST ENDPOINTS - New Comprehensive Tests
        // ========================================
        
        get("/api/tests/async-basic") {
            logger.info("Running Test: Basic vizAsync")
            try {
                val vizMain = VizEventMain()
                vizMain.exampleOfTwoAsyncs()
                call.respond(HttpStatusCode.OK, ScenarioResponse(
                    success = true,
                    message = "✅ Test completed! Check logs for details."
                ))
            } catch (e: Exception) {
                logger.error("Test failed", e)
                call.respond(HttpStatusCode.InternalServerError, ScenarioResponse(
                    success = false,
                    message = "❌ Test failed: ${e.message}"
                ))
            }
        }
        
        get("/api/tests/exception-propagation") {
            logger.info("Running Test: Exception Propagation (CRITICAL TEST)")
            try {
                val vizMain = VizEventMain()
                vizMain.testExceptionPropagation()
                call.respond(HttpStatusCode.OK, ScenarioResponse(
                    success = true,
                    message = "✅ Test completed! Check logs to verify structured concurrency."
                ))
            } catch (e: Exception) {
                logger.error("Test failed", e)
                call.respond(HttpStatusCode.InternalServerError, ScenarioResponse(
                    success = false,
                    message = "❌ Test failed: ${e.message}"
                ))
            }
        }
        
        get("/api/tests/mixed-launch-async") {
            logger.info("Running Test: Mixed Launch + Async with Exception")
            try {
                val vizMain = VizEventMain()
                vizMain.testMixedLaunchAsyncWithException()
                call.respond(HttpStatusCode.OK, ScenarioResponse(
                    success = true,
                    message = "✅ Test completed! Check logs for details."
                ))
            } catch (e: Exception) {
                logger.error("Test failed", e)
                call.respond(HttpStatusCode.InternalServerError, ScenarioResponse(
                    success = false,
                    message = "❌ Test failed: ${e.message}"
                ))
            }
        }
        
        get("/api/tests/multiple-awaiters") {
            logger.info("Running Test: Multiple Awaiters on Same Deferred")
            try {
                val vizMain = VizEventMain()
                vizMain.testMultipleAwaiters()
                call.respond(HttpStatusCode.OK, ScenarioResponse(
                    success = true,
                    message = "✅ Test completed! Check logs for details."
                ))
            } catch (e: Exception) {
                logger.error("Test failed", e)
                call.respond(HttpStatusCode.InternalServerError, ScenarioResponse(
                    success = false,
                    message = "❌ Test failed: ${e.message}"
                ))
            }
        }
        
        get("/api/tests/run-all") {
            logger.info("Running ALL TESTS")
            try {
                val vizMain = VizEventMain()
                vizMain.runAllTests()
                call.respond(HttpStatusCode.OK, ScenarioResponse(
                    success = true,
                    message = "✅ All tests completed! Check logs for detailed results."
                ))
            } catch (e: Exception) {
                logger.error("Test suite failed", e)
                call.respond(HttpStatusCode.InternalServerError, ScenarioResponse(
                    success = false,
                    message = "❌ Test suite failed: ${e.message}"
                ))
            }
        }
        
        get("/api/tests/dispatcher-tracking") {
            logger.info("Running Test: Dispatcher Tracking with VizDispatchers")
            try {
                val vizMain = VizEventMain()
                vizMain.testDispatcherTracking()
                call.respond(HttpStatusCode.OK, ScenarioResponse(
                    success = true,
                    message = "✅ Dispatcher test completed! Check logs for event details."
                ))
            } catch (e: Exception) {
                logger.error("Dispatcher test failed", e)
                call.respond(HttpStatusCode.InternalServerError, ScenarioResponse(
                    success = false,
                    message = "❌ Dispatcher test failed: ${e.message}"
                ))
            }
        }
        
        get("/api/examples/dispatcher-scenario") {
            logger.info("Running Example: Dispatcher Scenario")
            try {
                com.jh.proj.coroutineviz.examples.dispatcherExampleScenario()
                call.respond(HttpStatusCode.OK, ScenarioResponse(
                    success = true,
                    message = "✅ Dispatcher example completed! Check logs for details."
                ))
            } catch (e: Exception) {
                logger.error("Dispatcher example failed", e)
                call.respond(HttpStatusCode.InternalServerError, ScenarioResponse(
                    success = false,
                    message = "❌ Dispatcher example failed: ${e.message}"
                ))
            }
        }

        // Run scenario and return the captured data
        get("/api/viz/run-scenario-with-data") {
            val sessionId = "api-session-${System.currentTimeMillis()}"
            logger.info("┌─────────────────────────────────────────┐")
            logger.info("│  Starting Scenario with Data Capture   │")
            logger.info("│  Session: $sessionId".padEnd(42) + "│")
            logger.info("└─────────────────────────────────────────┘")

            try {
                val session = VizSession(sessionId = sessionId)
                logger.debug("✓ VizSession created with sessionId: $sessionId")

                // Execute scenario using the session
                logger.debug("⚙️  Executing scenario and capturing data...")
                val result = runScenarioAndCaptureData(session)

                logger.info("✅ Scenario completed | Events: ${result.eventCount} | Coroutines: ${result.coroutines.size}")
                logger.debug("📋 Coroutines in snapshot: ${result.coroutines.map { it.id }}")

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                logger.error("❌ Error executing scenario with data capture for session: $sessionId", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ScenarioResponse(
                        success = false,
                        message = "Error executing scenario: ${e.message}"
                    )
                )
            }
        }

        // ========================================
        // Session Management API
        // ========================================

        // Create a new session
        post("/api/sessions") {
            val name = call.request.queryParameters["name"]
            val session = SessionManager.createSession(name)
            
            logger.info("Created new session via API: ${session.sessionId}")
            
            call.respond(
                HttpStatusCode.Created,
                mapOf(
                    "sessionId" to session.sessionId,
                    "message" to "Session created successfully"
                )
            )
        }

        // List all sessions
        get("/api/sessions") {
            val sessions = SessionManager.listSessions()
            logger.debug("Listing sessions: ${sessions.size} active")
            call.respond(HttpStatusCode.OK, sessions)
        }

        // Get session snapshot
        get("/api/sessions/{id}") {
            val sessionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
                return@get
            }

            val session = SessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            val snapshot = SessionSnapshotResponse(
                sessionId = session.sessionId,
                coroutineCount = session.snapshot.coroutines.size,
                eventCount = session.store.all().size,
                coroutines = session.snapshot.coroutines.values.map { node ->
                    CoroutineNodeDto(
                        id = node.id,
                        jobId = node.jobId,
                        parentId = node.parentId,
                        scopeId = node.scopeId,
                        label = node.label,
                        state = node.state.toString()
                    )
                }
            )

            call.respond(HttpStatusCode.OK, snapshot)
        }

        // Delete a session
        delete("/api/sessions/{id}") {
            val sessionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
                return@delete
            }

            val success = SessionManager.closeSession(sessionId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Session closed"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            }
        }

        // Get all events from a session
        get("/api/sessions/{id}/events") {
            val sessionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
                return@get
            }

            val session = SessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            val events = session.store.all()
            call.respond(HttpStatusCode.OK, events)
        }

        // Get hierarchy tree for a session
        get("/api/sessions/{id}/hierarchy") {
            val sessionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
                return@get
            }

            val session = SessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            val scopeId = call.request.queryParameters["scopeId"]
            val tree = session.projectionService.getHierarchyTree(scopeId)
            
            call.respond(HttpStatusCode.OK, tree)
        }

        // Get thread activity for a session
        get("/api/sessions/{id}/threads") {
            val sessionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
                return@get
            }

            val session = SessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            val activity = session.projectionService.getThreadActivity()
            
            call.respond(HttpStatusCode.OK, activity)
        }

        // Get timeline for specific coroutine
        get("/api/sessions/{id}/coroutines/{coroutineId}/timeline") {
            val sessionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session ID"))
                return@get
            }
            
            val coroutineId = call.parameters["coroutineId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing coroutine ID"))
                return@get
            }

            val session = SessionManager.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            val timeline = session.projectionService.getCoroutineTimeline(coroutineId)
            if (timeline == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Coroutine not found"))
                return@get
            }
            
            call.respond(HttpStatusCode.OK, timeline)
        }

        // Stream events via SSE
        sse("/api/sessions/{id}/stream") {
            val sessionId = call.parameters["id"] ?: run {
                logger.warn("SSE connection attempted without session ID")
                return@sse
            }

            val session = SessionManager.getSession(sessionId)
            if (session == null) {
                logger.warn("SSE connection attempted for non-existent session: $sessionId")
                send(ServerSentEvent(
                    data = """{"error": "Session not found"}""",
                    event = "error"
                ))
                return@sse
            }

            logger.info("SSE stream started for session: $sessionId")

            try {
                session.bus.stream().collect { event ->
                    send(ServerSentEvent(
                        data = kotlinx.serialization.json.Json.encodeToString(event),
                        event = event.kind,
                        id = "${event.sessionId}-${event.seq}"
                    ))
                }
            } catch (e: Exception) {
                logger.error("Error in SSE stream for session $sessionId", e)
            } finally {
                logger.info("SSE stream ended for session: $sessionId")
            }
        }

        // ========================================
        // Scenario Runners (using SessionManager)
        // ========================================

        // Helper function to get or create session
        suspend fun getOrCreateSession(sessionId: String?): VizSession {
            return sessionId?.let { SessionManager.getSession(it) }
                ?: SessionManager.createSession("auto-${System.currentTimeMillis()}")
        }

        // Run nested coroutines scenario
        post("/api/scenarios/nested") {
            val sessionId = call.request.queryParameters["sessionId"]
            val session = getOrCreateSession(sessionId)

            logger.info("Running nested coroutines scenario in session: ${session.sessionId}")

            try {
                val job = ScenarioRunner.runNestedCoroutines(session)
                job.join()
                
                // Small delay to ensure all events are processed
                delay(100)

                call.respond(
                    HttpStatusCode.OK,
                    ScenarioCompletionResponse(
                        success = true,
                        sessionId = session.sessionId,
                        message = "Scenario completed. Connect to /api/sessions/${session.sessionId}/stream for live events.",
                        coroutineCount = session.snapshot.coroutines.size,
                        eventCount = session.store.all().size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error running nested scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Scenario failed: ${e.message}")
                )
            }
        }

        // Run parallel execution scenario
        post("/api/scenarios/parallel") {
            val sessionId = call.request.queryParameters["sessionId"]
            val session = getOrCreateSession(sessionId)

            logger.info("Running parallel execution scenario in session: ${session.sessionId}")

            try {
                val job = ScenarioRunner.runParallelExecution(session)
                job.join()
                delay(100)

                call.respond(
                    HttpStatusCode.OK,
                    ScenarioCompletionResponse(
                        success = true,
                        sessionId = session.sessionId,
                        message = "Parallel scenario completed",
                        coroutineCount = session.snapshot.coroutines.size,
                        eventCount = session.store.all().size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error running parallel scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Scenario failed: ${e.message}")
                )
            }
        }

        // Run cancellation scenario
        post("/api/scenarios/cancellation") {
            val sessionId = call.request.queryParameters["sessionId"]
            val session = getOrCreateSession(sessionId)

            logger.info("Running cancellation scenario in session: ${session.sessionId}")

            try {
                val job = ScenarioRunner.runCancellationScenario(session)
                job.join()
                delay(100)

                call.respond(
                    HttpStatusCode.OK,
                    ScenarioCompletionResponse(
                        success = true,
                        sessionId = session.sessionId,
                        message = "Cancellation scenario completed",
                        coroutineCount = session.snapshot.coroutines.size,
                        eventCount = session.store.all().size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error running cancellation scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Scenario failed: ${e.message}")
                )
            }
        }

        // Run deep nesting scenario
        post("/api/scenarios/deep-nesting") {
            val sessionId = call.request.queryParameters["sessionId"]
            val depth = call.request.queryParameters["depth"]?.toIntOrNull() ?: 5
            val session = getOrCreateSession(sessionId)

            logger.info("Running deep nesting scenario (depth=$depth) in session: ${session.sessionId}")

            try {
                val job = ScenarioRunner.runDeepNesting(session, depth)
                job.join()
                delay(100)

                call.respond(
                    HttpStatusCode.OK,
                    ScenarioCompletionResponse(
                        success = true,
                        sessionId = session.sessionId,
                        message = "Deep nesting scenario completed (depth=$depth)",
                        coroutineCount = session.snapshot.coroutines.size,
                        eventCount = session.store.all().size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error running deep nesting scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Scenario failed: ${e.message}")
                )
            }
        }

        // Run mixed scenario
        post("/api/scenarios/mixed") {
            val sessionId = call.request.queryParameters["sessionId"]
            val session = getOrCreateSession(sessionId)

            logger.info("Running mixed scenario in session: ${session.sessionId}")

            try {
                val job = ScenarioRunner.runMixedScenario(session)
                job.join()
                delay(100)

                call.respond(
                    HttpStatusCode.OK,
                    ScenarioCompletionResponse(
                        success = true,
                        sessionId = session.sessionId,
                        message = "Mixed scenario completed",
                        coroutineCount = session.snapshot.coroutines.size,
                        eventCount = session.store.all().size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error running mixed scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Scenario failed: ${e.message}")
                )
            }
        }

        // Run exception scenario
        post("/api/scenarios/exception") {
            val sessionId = call.request.queryParameters["sessionId"]
            val session = getOrCreateSession(sessionId)

            logger.info("Running exception scenario in session: ${session.sessionId}")

            try {
                val job = ScenarioRunner.runExceptionScenario(session)
                job.join()
                delay(100)

                call.respond(
                    HttpStatusCode.OK,
                    ScenarioCompletionResponse(
                        success = true,
                        sessionId = session.sessionId,
                        message = "Exception scenario completed",
                        coroutineCount = session.snapshot.coroutines.size,
                        eventCount = session.store.all().size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error running exception scenario", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Scenario failed: ${e.message}")
                )
            }
        }

        // Run custom scenario from configuration
        post("/api/scenarios/custom") {
            logger.info("Received custom scenario configuration request")

            try {
                // Parse the scenario configuration from request body
                val request = call.receive<com.jh.proj.coroutineviz.scenarios.ScenarioConfigRequest>()
                
                logger.info("Custom scenario: ${request.name}")
                logger.debug("Description: ${request.description}")
                
                // Get or create session
                val session = getOrCreateSession(request.sessionId)
                logger.info("Using session: ${session.sessionId}")

                // Convert DTO to internal config
                val scenarioConfig = request.toScenarioConfig()
                
                // Execute the custom scenario
                val job = ScenarioRunner.runCustomScenario(session, scenarioConfig)
                job.join()
                
                // Small delay to ensure all events are processed
                delay(100)

                val response = com.jh.proj.coroutineviz.scenarios.ScenarioExecutionResponse(
                    success = true,
                    sessionId = session.sessionId,
                    message = "Custom scenario '${request.name}' completed successfully",
                    coroutineCount = session.snapshot.coroutines.size,
                    eventCount = session.store.all().size
                )
                
                logger.info("✅ Custom scenario completed successfully | " +
                    "Coroutines: ${response.coroutineCount} | Events: ${response.eventCount}")
                
                call.respond(HttpStatusCode.OK, response)

            } catch (e: IllegalArgumentException) {
                logger.error("Invalid scenario configuration", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    com.jh.proj.coroutineviz.scenarios.ScenarioExecutionResponse(
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
                    com.jh.proj.coroutineviz.scenarios.ScenarioExecutionResponse(
                        success = false,
                        sessionId = "",
                        message = "Error executing scenario: ${e.message}",
                        errors = listOf(e.stackTraceToString())
                    )
                )
            }
        }

        // List available scenarios
        get("/api/scenarios") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "scenarios" to listOf(
                        mapOf(
                            "id" to "nested",
                            "name" to "Nested Coroutines",
                            "description" to "Demonstrates parent-child relationships and structured concurrency",
                            "endpoint" to "/api/scenarios/nested"
                        ),
                        mapOf(
                            "id" to "parallel",
                            "name" to "Parallel Execution",
                            "description" to "Multiple coroutines running in parallel",
                            "endpoint" to "/api/scenarios/parallel"
                        ),
                        mapOf(
                            "id" to "cancellation",
                            "name" to "Cancellation",
                            "description" to "Demonstrates coroutine cancellation and cleanup",
                            "endpoint" to "/api/scenarios/cancellation"
                        ),
                        mapOf(
                            "id" to "deep-nesting",
                            "name" to "Deep Nesting",
                            "description" to "Deep hierarchy of nested coroutines (configurable depth)",
                            "endpoint" to "/api/scenarios/deep-nesting?depth=5"
                        ),
                        mapOf(
                            "id" to "mixed",
                            "name" to "Mixed Sequential/Parallel",
                            "description" to "Combination of sequential and parallel execution",
                            "endpoint" to "/api/scenarios/mixed"
                        ),
                        mapOf(
                            "id" to "exception",
                            "name" to "Exception Handling",
                            "description" to "Demonstrates exception tracking and failure states",
                            "endpoint" to "/api/scenarios/exception"
                        )
                    )
                )
            )
        }
    }
}

@Serializable
data class SessionSnapshotResponse(
    val sessionId: String,
    val coroutineCount: Int,
    val eventCount: Int,
    val coroutines: List<CoroutineNodeDto>
)

@Serializable
data class ScenarioCompletionResponse(
    val success: Boolean,
    val sessionId: String,
    val message: String,
    val coroutineCount: Int,
    val eventCount: Int
)

/**
 * Run the scenario and capture all events and snapshot data
 */
private suspend fun runScenarioAndCaptureData(session: VizSession): ScenarioResultData {
    logger.debug("runScenarioAndCaptureData started for session: ${session.sessionId}")

    // Execute the same logic as VizEventMain.main()
    val cid = "coro-1"
    val jid = "job-1"
    val parent: String? = null
    val scopeId = "scope-1"
    val label = "root"

    fun nowSeq() = session.nextSeq()
    fun nowTs() = System.nanoTime()

    logger.debug("Sending CoroutineCreated event for coroutineId: $cid")
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

    logger.debug("Sending CoroutineStarted event for coroutineId: $cid")
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

    logger.debug("Sending CoroutineCompleted event for coroutineId: $cid")
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

    logger.debug("Events sent, waiting for processing...")
    // Small delay to ensure all events are processed
    delay(100)

    logger.debug("Capturing snapshot data...")
    // Capture snapshot data
    val coroutineNodes = session.snapshot.coroutines.values.map { node ->
        logger.trace("Processing coroutine node: id=${node.id}, state=${node.state}")
        CoroutineNodeDto(
            id = node.id,
            jobId = node.jobId,
            parentId = node.parentId,
            scopeId = node.scopeId,
            label = node.label,
            state = node.state.toString()
        )
    }

    val allEvents = session.store.all()
    logger.info("Scenario data capture complete: ${allEvents.size} events, ${coroutineNodes.size} coroutines")

    return ScenarioResultData(
        success = true,
        sessionId = session.sessionId,
        events = allEvents,
        coroutines = coroutineNodes,
        eventCount = allEvents.size
    )
}

@Serializable
data class ScenarioResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class ScenarioResultData(
    val success: Boolean,
    val sessionId: String,
    val events: List<com.jh.proj.coroutineviz.events.VizEvent>,
    val coroutines: List<CoroutineNodeDto>,
    val eventCount: Int
)

@Serializable
data class CoroutineNodeDto(
    val id: String,
    val jobId: String,
    val parentId: String?,
    val scopeId: String,
    val label: String?,
    val state: String
)
