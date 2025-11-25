package com.jh.proj.coroutineviz.examples

import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizJob
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * Examples demonstrating VizJob usage for tracking job operations like cancel(), join(), etc.
 */
class VizJobExample {
//    private val logger = LoggerFactory.getLogger(VizJobExample::class.java)
//
//    /**
//     * Demonstrates explicit job cancellation with VizJob tracking.
//     *
//     * Events emitted:
//     * 1. CoroutineCreated, CoroutineStarted for each coroutine
//     * 2. JobJoinRequested when parent calls child2.join()
//     * 3. JobJoinCompleted when child2 completes
//     * 4. JobCancellationRequested when parent calls child1.cancel()
//     * 5. CoroutineCancelled when child1 is actually cancelled
//     * 6. CoroutineCompleted for successfully completed coroutines
//     */
//    suspend fun runCancellationScenario(session: VizSession): VizJob = coroutineScope {
//        logger.info("Starting cancellation scenario in session: ${session.sessionId}")
//
//        val viz = VizScope(session)
//
//        val parentJob = viz.vizLaunch("parent") {
//            val child1: VizJob = vizLaunch("child-to-be-cancelled") {
//                logger.debug("Child starting long operation...")
//                try {
//                    vizDelay(5000) // Long delay
//                    logger.debug("Child completed (should not reach here)")
//                } catch (e: CancellationException) {
//                    logger.debug("Child was cancelled")
//                    throw e
//                }
//            }
//
//            val child2: VizJob = vizLaunch("normal-child") {
//                logger.debug("Normal child running")
//                vizDelay(100)
//                logger.debug("Normal child completed")
//            }
//
//            // Wait for normal child - emits JobJoinRequested and JobJoinCompleted
//            child2.join()
//
//            // Cancel the long-running child - emits JobCancellationRequested
//            logger.debug("Cancelling long-running child...")
//            child1.cancel()
//
//            logger.debug("Parent completed")
//        }
//
//        logger.info("Waiting for cancellation scenario to complete...")
//        parentJob
//    }
//
//    /**
//     * Demonstrates cancelAndJoin pattern with VizJob.
//     * Shows how a parent can cancel and wait for a child in one operation.
//     */
//    suspend fun runCancelAndJoinScenario(session: VizSession): VizJob = coroutineScope {
//        logger.info("Starting cancel-and-join scenario")
//
//        val viz = VizScope(session)
//
//        val parentJob = viz.vizLaunch("parent") {
//            val child: VizJob = vizLaunch("long-running-child") {
//                try {
//                    logger.debug("Child working...")
//                    vizDelay(10000)
//                    logger.debug("Child done (should not reach)")
//                } catch (e: CancellationException) {
//                    logger.debug("Child cancelled")
//                    throw e
//                }
//            }
//
//            vizDelay(100) // Let child start
//
//            // Cancel and wait in one operation
//            // Emits: JobCancellationRequested, JobJoinRequested, JobJoinCompleted
//            logger.debug("Cancelling and joining child...")
//            child.cancelAndJoin()
//
//            logger.debug("Parent completed")
//        }
//
//        parentJob
//    }
//
//    /**
//     * Demonstrates child failure propagation with VizJob tracking.
//     * Shows how exceptions in children affect parents in structured concurrency.
//     */
//    suspend fun runChildFailureScenario(session: VizSession): VizJob = coroutineScope {
//        logger.info("Starting child failure scenario")
//
//        val viz = VizScope(session)
//
//        val parentJob = viz.vizLaunch("parent") {
//            try {
//                val child1: VizJob = vizLaunch("failing-child") {
//                    logger.debug("Child about to fail...")
//                    vizDelay(100)
//                    error("Intentional failure!") // This will emit CoroutineFailed
//                }
//
//                val child2: VizJob = vizLaunch("normal-child") {
//                    logger.debug("Normal child working...")
//                    vizDelay(500)
//                    logger.debug("Normal child done (might not reach)")
//                }
//
//                // Parent waits for children (structured concurrency)
//                // When child1 fails, parent gets cancelled
//                logger.debug("Parent waiting for children...")
//
//            } catch (e: Exception) {
//                logger.debug("Parent caught exception: ${e.message}")
//                throw e
//            }
//        }
//
//        parentJob
//    }
//
//    /**
//     * Demonstrates multiple children with different completion patterns.
//     * Shows complex job interactions with proper event tracking.
//     */
//    suspend fun runComplexJobInteractions(session: VizSession): VizJob = coroutineScope {
//        logger.info("Starting complex job interactions")
//
//        val viz = VizScope(session)
//
//        val parentJob = viz.vizLaunch("orchestrator") {
//            val jobs = mutableListOf<VizJob>()
//
//            // Launch multiple workers
//            repeat(3) { i ->
//                val job = vizLaunch("worker-$i") {
//                    logger.debug("Worker $i starting...")
//                    vizDelay(100L * (i + 1))
//                    logger.debug("Worker $i completed")
//                }
//                jobs.add(job)
//            }
//
//            // Wait for specific jobs
//            logger.debug("Waiting for worker-0...")
//            jobs[0].join() // Emits JobJoinRequested/Completed
//
//            logger.debug("Cancelling worker-2...")
//            jobs[2].cancel() // Emits JobCancellationRequested
//
//            // Wait for all remaining
//            logger.debug("Waiting for all jobs...")
//            jobs.forEach { it.join() }
//
//            logger.debug("Orchestrator completed")
//        }
//
//        parentJob
//    }
//
//    /**
//     * Demonstrates timeout scenario with VizJob.
//     * Shows how timeouts interact with job cancellation.
//     */
//    suspend fun runTimeoutScenario(session: VizSession): VizJob = coroutineScope {
//        logger.info("Starting timeout scenario")
//
//        val viz = VizScope(session)
//
//        val parentJob = viz.vizLaunch("parent-with-timeout") {
//            val slowChild: VizJob = vizLaunch("slow-child") {
//                try {
//                    logger.debug("Slow child working...")
//                    vizDelay(5000)
//                    logger.debug("Slow child done (should not reach)")
//                } catch (e: CancellationException) {
//                    logger.debug("Slow child timed out")
//                    throw e
//                }
//            }
//
//            // Wait with timeout
//            try {
//                withTimeout(1000) {
//                    slowChild.join()
//                }
//                logger.debug("Child completed in time")
//            } catch (e: TimeoutCancellationException) {
//                logger.debug("Timeout! Cancelling child...")
//                slowChild.cancel()
//                logger.debug("Parent handling timeout")
//            }
//        }
//
//        parentJob
//    }
//
//    companion object {
//        /**
//         * Compare classic coroutines vs VizJob-wrapped version.
//         */
//        suspend fun compareClassicVsViz() {
//            println("=== CLASSIC COROUTINES ===")
//            classicCancellation()
//
//            println("\n=== WITH VIZ JOB TRACKING ===")
//            val session = VizSession("demo-session")
//            val example = VizJobExample()
//            example.runCancellationScenario(session).join()
//        }
//
//        /**
//         * Classic coroutine version without visualization.
//         */
//        private suspend fun classicCancellation() = coroutineScope {
//            val job = launch {
//                val child1 = launch {
//                    try {
//                        delay(5000)
//                    } catch (e: CancellationException) {
//                        println("Child cancelled")
//                        throw e
//                    }
//                }
//
//                val child2 = launch {
//                    delay(100)
//                }
//
//                child2.join()
//                child1.cancel()
//            }
//
//            job.join()
//        }
//    }
}


