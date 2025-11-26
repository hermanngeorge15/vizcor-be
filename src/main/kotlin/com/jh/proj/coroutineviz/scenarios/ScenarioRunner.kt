package com.jh.proj.coroutineviz.scenarios

import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * Collection of coroutine scenarios for visualization and teaching.
 */
object ScenarioRunner {
    private val logger = LoggerFactory.getLogger(ScenarioRunner::class.java)

    /**
     * Simple nested coroutines scenario demonstrating parent-child relationships.
     */
    suspend fun runNestedCoroutines(session: VizSession): Job = coroutineScope {
        logger.info("Starting nested coroutines scenario in session: ${session.sessionId}")
        
        val viz = VizScope(session)
        
        val job = viz.vizLaunch("parent") {
            logger.debug("Parent coroutine started")
            
            vizLaunch("child-1") {
                logger.debug("Child-1 started")
                
                vizLaunch("child-1-1") {
                    logger.debug("Child-1-1 started")
                    vizDelay(5000)
                    logger.debug("Child-1-1 completed")
                }
                
                vizDelay(2000)
                logger.debug("Child-1 completed")
            }
            
            vizLaunch("child-2") {
                logger.debug("Child-2 started")
                vizDelay(4000)
                logger.debug("Child-2 completed")
            }
            
            logger.debug("Parent completed")
        }
        
        logger.info("Waiting for nested coroutines to complete...")
        job
    }

    /**
     * Parallel execution scenario with multiple coroutines.
     */
    suspend fun runParallelExecution(session: VizSession): Job = coroutineScope {
        logger.info("Starting parallel execution scenario in session: ${session.sessionId}")
        
        val viz = VizScope(session)
        
        val job = viz.vizLaunch("coordinator") {
            val jobs = List(5) { index ->
                vizLaunch("worker-$index") {
                    val workTime = (100..300).random()
                    logger.debug("Worker-$index starting (will work for ${workTime}ms)")
                    vizDelay(workTime.toLong())
                    logger.debug("Worker-$index completed")
                }
            }
            
            // Wait for all workers
            jobs.forEach { it.join() }
            logger.debug("All workers completed")
        }
        
        logger.info("Waiting for parallel execution to complete...")
        job
    }

    /**
     * Cancellation scenario demonstrating structured concurrency.
     */
    suspend fun runCancellationScenario(session: VizSession): Job = coroutineScope {
        logger.info("Starting cancellation scenario in session: ${session.sessionId}")
        
        val viz = VizScope(session)
        
        val job = viz.vizLaunch("parent") {
            val child1 = vizLaunch("child-to-be-cancelled") {
                logger.debug("Child starting long operation...")
                try {
                    vizDelay(5000) // Long delay
                    logger.debug("Child completed (should not reach here)")
                } catch (e: CancellationException) {
                    logger.debug("Child was cancelled")
                    throw e
                }
            }
            
            val child2 = vizLaunch("normal-child") {
                logger.debug("Normal child running")
                vizDelay(100)
                logger.debug("Normal child completed")
            }
            
            // Wait for normal child
            child2.join()
            
            // Cancel the long-running child
            logger.debug("Cancelling long-running child...")
            child1.cancel()
            
            logger.debug("Parent completed")
        }
        
        logger.info("Waiting for cancellation scenario to complete...")
        job
    }

    /**
     * Deep nesting scenario for testing hierarchy visualization.
     */
    suspend fun runDeepNesting(session: VizSession, depth: Int = 5): Job = coroutineScope {
        logger.info("Starting deep nesting scenario (depth=$depth) in session: ${session.sessionId}")
        
        val viz = VizScope(session)
        
        suspend fun VizScope.createNested(level: Int): Job {
            return if (level >= depth) {
                vizLaunch("leaf-$level") {
                    vizDelay(5000)
                    logger.debug("Leaf at level $level completed")
                }
            } else {
                vizLaunch("level-$level") {
                    logger.debug("Level $level started")
                    createNested(level + 1).join()
                    logger.debug("Level $level completed")
                }
            }
        }
        
        val job = viz.createNested(0)
        
        logger.info("Waiting for deep nesting to complete...")
        job
    }

    /**
     * Mixed scenario with sequential and parallel execution.
     */
    suspend fun runMixedScenario(session: VizSession): Job = coroutineScope {
        logger.info("Starting mixed scenario in session: ${session.sessionId}")
        
        val viz = VizScope(session)
        
        val job = viz.vizLaunch("orchestrator") {
            // Phase 1: Sequential setup
            vizLaunch("setup-phase") {
                logger.debug("Setup starting...")
                vizDelay(100)
                logger.debug("Setup completed")
            }.join()
            
            // Phase 2: Parallel work
            logger.debug("Starting parallel work phase...")
            val workers = List(3) { index ->
                vizLaunch("parallel-worker-$index") {
                    vizDelay((50..150).random().toLong())
                    logger.debug("Parallel worker $index completed")
                }
            }
            workers.forEach { it.join() }
            
            // Phase 3: Sequential cleanup
            vizLaunch("cleanup-phase") {
                logger.debug("Cleanup starting...")
                vizDelay(100)
                logger.debug("Cleanup completed")
            }.join()
            
            logger.debug("Orchestrator completed")
        }
        
        logger.info("Waiting for mixed scenario to complete...")
        job
    }

    suspend fun runExceptionScenario(session: VizSession): Job = coroutineScope {
        logger.info("Starting exception scenario in session: ${session.sessionId}")

        val viz = VizScope(session)

        val job = viz.vizLaunch("parent") {
            val child1 = vizLaunch("failing-child") {
                logger.debug("Child about to fail...")
                vizDelay(100)
                throw IllegalStateException("Intentional failure for demo")
            }

            val child2 = vizLaunch("normal-child") {
                logger.debug("Normal child running")
                vizDelay(200)
                logger.debug("Normal child completed")
            }

            try {
                child1.join()
                child2.join()
            } catch (e: Exception) {
                logger.debug("Parent caught exception: ${e.message}")
            }
        }

        logger.info("Waiting for exception scenario to complete...")
        job
    }

    /**
     * Run a custom scenario from configuration.
     * This allows dynamic scenario creation from the frontend.
     */
    suspend fun runCustomScenario(session: VizSession, config: ScenarioConfig): Job = coroutineScope {
        logger.info("Starting custom scenario '${config.name}' in session: ${session.sessionId}")
        if (config.description != null) {
            logger.info("Description: ${config.description}")
        }
        
        val viz = VizScope(session)
        
        // Execute the root coroutine configuration recursively
        val job = viz.executeCoroutineConfig(config.root)
        
        logger.info("Custom scenario '${config.name}' launched, waiting for completion...")
        job
    }
}

