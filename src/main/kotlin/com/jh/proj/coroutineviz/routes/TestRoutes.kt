package com.jh.proj.coroutineviz.routes

import com.jh.proj.coroutineviz.session.VizEventMain
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CoroutineVizRouting")

fun Route.registerTestRoutes() {
    get("/api/tests/async-basic") {
        call.runVizTest("Basic vizAsync") {
            exampleOfTwoAsyncs()
        }
    }

    get("/api/tests/exception-propagation") {
        call.runVizTest("Exception Propagation (CRITICAL TEST)") {
            testExceptionPropagation()
        }
    }

    get("/api/tests/mixed-launch-async") {
        call.runVizTest("Mixed Launch + Async with Exception") {
            testMixedLaunchAsyncWithException()
        }
    }

    get("/api/tests/multiple-awaiters") {
        call.runVizTest("Multiple Awaiters on Same Deferred") {
            testMultipleAwaiters()
        }
    }

    get("/api/tests/run-all") {
        call.runVizTest("ALL TESTS") {
            runAllTests()
        }
    }

    get("/api/tests/dispatcher-tracking") {
        call.runVizTest("Dispatcher Tracking with VizDispatchers") {
            testDispatcherTracking()
        }
    }

    // ========================================================================
    // JOB STATUS TRACKING TESTS
    // ========================================================================

    get("/api/tests/waiting-for-children") {
        call.runVizTest("Waiting for Children - Basic") {
            testWaitingForChildren()
        }
    }

    get("/api/tests/nested-waiting") {
        call.runVizTest("Nested Waiting - Multi-level Hierarchy") {
            testNestedWaitingForChildren()
        }
    }

    get("/api/tests/mixed-waiting") {
        call.runVizTest("Waiting with Mixed Launch + Async") {
            testWaitingWithMixedChildren()
        }
    }

    get("/api/tests/cancel-waiting") {
        call.runVizTest("Cancellation During Waiting") {
            testCancellationDuringWaiting()
        }
    }

    get("/api/tests/progress-tracking") {
        call.runVizTest("Progress Tracking - Children Over Time") {
            testProgressTracking()
        }
    }

    get("/api/tests/job-status-all") {
        call.runVizTest("ALL JOB STATUS TESTS") {
            runJobStatusTests()
        }
    }
}

private suspend fun ApplicationCall.runVizTest(
    label: String,
    block: suspend VizEventMain.() -> Unit
) {
    logger.info("Running Test: $label")
    try {
        val vizMain = VizEventMain()
        vizMain.block()
        respond(
            HttpStatusCode.OK,
            ScenarioResponse(
                success = true,
                message = "✅ Test completed! Check logs for details."
            )
        )
    } catch (e: Exception) {
        logger.error("Test '$label' failed", e)
        respond(
            HttpStatusCode.InternalServerError,
            ScenarioResponse(
                success = false,
                message = "❌ Test failed: ${e.message}"
            )
        )
    }
}

