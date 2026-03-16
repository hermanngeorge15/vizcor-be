package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.scenarios.SyncScenarios
import com.jh.proj.coroutineviz.session.SessionManager
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizScope
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SyncScenarioRoutes")

@Serializable
data class SyncScenarioResponse(
    val success: Boolean,
    val sessionId: String,
    val scenario: String,
    val message: String,
    val eventCount: Int = 0
)

/**
 * Routes for synchronization primitive demonstration scenarios.
 * 
 * These routes demonstrate real-world uses of Mutex and Semaphore
 * with full event visualization.
 */
fun Route.registerSyncScenarioRoutes() {
    
    // ========================================================================
    // MUTEX SCENARIOS
    // ========================================================================
    
    /**
     * Thread-safe counter using Mutex
     */
    get("/api/sync/mutex/counter") {
        call.runSyncScenario("Thread-Safe Counter") { scope ->
            SyncScenarios.threadSafeCounter(scope)
        }
    }
    
    /**
     * Bank account transfer with consistent lock ordering
     */
    get("/api/sync/mutex/bank-transfer") {
        call.runSyncScenario("Bank Account Transfer") { scope ->
            SyncScenarios.bankAccountTransfer(scope)
        }
    }
    
    /**
     * Cache with thread-safe read-through pattern
     */
    get("/api/sync/mutex/cache") {
        call.runSyncScenario("Cache Read-Through") { scope ->
            SyncScenarios.cacheWithReadThrough(scope)
        }
    }
    
    /**
     * Deadlock demonstration (intentional)
     */
    get("/api/sync/mutex/deadlock-demo") {
        call.runSyncScenario("Deadlock Demonstration") { scope ->
            SyncScenarios.deadlockDemonstration(scope)
        }
    }
    
    // ========================================================================
    // SEMAPHORE SCENARIOS
    // ========================================================================
    
    /**
     * Database connection pool limiting
     */
    get("/api/sync/semaphore/connection-pool") {
        call.runSyncScenario("Database Connection Pool") { scope ->
            SyncScenarios.databaseConnectionPool(scope)
        }
    }
    
    /**
     * API rate limiter
     */
    get("/api/sync/semaphore/rate-limiter") {
        call.runSyncScenario("API Rate Limiter") { scope ->
            SyncScenarios.apiRateLimiter(scope)
        }
    }
    
    /**
     * Parallel file processor with I/O throttling
     */
    get("/api/sync/semaphore/file-processor") {
        call.runSyncScenario("Parallel File Processor") { scope ->
            SyncScenarios.parallelFileProcessor(scope)
        }
    }
    
    /**
     * Resource pool with timeout handling
     */
    get("/api/sync/semaphore/resource-timeout") {
        call.runSyncScenario("Resource Pool with Timeout") { scope ->
            SyncScenarios.resourcePoolWithTimeout(scope)
        }
    }
    
    /**
     * Producer-Consumer with bounded buffer
     */
    get("/api/sync/semaphore/producer-consumer") {
        call.runSyncScenario("Producer-Consumer Buffer") { scope ->
            SyncScenarios.producerConsumerBuffer(scope)
        }
    }
    
    // ========================================================================
    // COMBINED SCENARIOS
    // ========================================================================
    
    /**
     * E-commerce order processing (Mutex + Semaphore)
     */
    get("/api/sync/combined/ecommerce") {
        call.runSyncScenario("E-commerce Order Processing") { scope ->
            SyncScenarios.ecommerceOrderProcessing(scope)
        }
    }
    
    // ========================================================================
    // LIST ALL SCENARIOS
    // ========================================================================
    
    get("/api/sync/scenarios") {
        call.respond(
            HttpStatusCode.OK,
            listOf(
                ScenarioInfo("mutex/counter", "Thread-Safe Counter", "Mutex", "Using mutex for atomic counter increments"),
                ScenarioInfo("mutex/bank-transfer", "Bank Account Transfer", "Mutex", "Safe money transfer with consistent lock ordering"),
                ScenarioInfo("mutex/cache", "Cache Read-Through", "Mutex", "Thread-safe lazy initialization pattern"),
                ScenarioInfo("mutex/deadlock-demo", "Deadlock Demo", "Mutex", "Intentional deadlock for detection demonstration"),
                ScenarioInfo("semaphore/connection-pool", "Connection Pool", "Semaphore", "Limiting concurrent database connections"),
                ScenarioInfo("semaphore/rate-limiter", "API Rate Limiter", "Semaphore", "Throttling concurrent API calls"),
                ScenarioInfo("semaphore/file-processor", "File Processor", "Semaphore", "I/O-limited parallel file processing"),
                ScenarioInfo("semaphore/resource-timeout", "Resource Timeout", "Semaphore", "Resource acquisition with timeout"),
                ScenarioInfo("semaphore/producer-consumer", "Producer-Consumer", "Semaphore", "Bounded buffer implementation"),
                ScenarioInfo("combined/ecommerce", "E-commerce Orders", "Combined", "Order processing with inventory and payment")
            )
        )
    }
}

@Serializable
data class ScenarioInfo(
    val path: String,
    val name: String,
    val type: String,
    val description: String
)

/**
 * Helper function to run a sync scenario with proper setup and response
 */
private suspend fun ApplicationCall.runSyncScenario(
    scenarioName: String,
    scenario: suspend (VizScope) -> Unit
) {
    logger.info("┌─────────────────────────────────────────┐")
    logger.info("│  Running Sync Scenario                  │")
    logger.info("│  Scenario: $scenarioName".padEnd(42) + "│")
    logger.info("└─────────────────────────────────────────┘")
    
    try {
        // Create session
        val session = SessionManager.createSession("sync-${scenarioName.lowercase().replace(" ", "-")}")
        
        // Create VizScope
        val scope = VizScope(session)
        
        // Run the scenario
        scenario(scope)
        
        // Wait for any pending events
        kotlinx.coroutines.delay(100)
        
        val eventCount = session.store.all().size
        
        logger.info("✅ Sync scenario '$scenarioName' completed | Events: $eventCount")
        
        respond(
            HttpStatusCode.OK,
            SyncScenarioResponse(
                success = true,
                sessionId = session.sessionId,
                scenario = scenarioName,
                message = "Scenario completed successfully",
                eventCount = eventCount
            )
        )
    } catch (e: Exception) {
        logger.error("❌ Sync scenario '$scenarioName' failed", e)
        respond(
            HttpStatusCode.InternalServerError,
            SyncScenarioResponse(
                success = false,
                sessionId = "",
                scenario = scenarioName,
                message = "Scenario failed: ${e.message}"
            )
        )
    }
}

