package com.jh.proj

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.VizEvent
import com.jh.proj.coroutineviz.models.CoroutineState
import com.jh.proj.coroutineviz.session.VizSession
import com.jh.proj.coroutineviz.wrappers.VizDispatchers
import com.jh.proj.coroutineviz.wrappers.VizScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class VizLaunchTest {
    private fun VizEvent.getLabel(): String? =
        (this as? CoroutineEvent)?.label

    @Test
    fun `viz launch - send notifications`() = runTest {
        val session = VizSession(sessionId = "notification-test")
        val dispatcher = VizDispatchers(session)
        val scope = VizScope(session, dispatcher.default)

        val eventLog = mutableListOf<String>()

        val eventLogger = launch {
            session.bus.stream().collect{ event ->
                when(event.kind){
                   "CoroutineCompleted" -> {
                       eventLog.add(event.getLabel() ?: "unknown")
                       logger.info("✅ COMPLETED: ${event.getLabel()}")
                   }
                }
            }
        }

        val parentLabel = "parent"
        val childOneLabel = "child-1"
        val childTwoLabel = "child-2"
        val childThreeLabel = "child-3"

        val job = scope.vizLaunch(label = parentLabel) {
            vizLaunch(label = childOneLabel) {
                vizDelay(1000)
            }
            vizLaunch(label = childTwoLabel) {
                vizDelay(500)
            }
            vizLaunch(label = childThreeLabel) {
                vizDelay(1500)
            }
        }

        job.join()

        eventLogger.cancel()

        val coroutinesLog = session.snapshot.coroutines.values
        val parent = coroutinesLog.find{ it.label == parentLabel}
        val childOne = coroutinesLog.find{ it.label == childOneLabel}
        val childTwo = coroutinesLog.find{ it.label == childTwoLabel}
        val childThree = coroutinesLog.find{ it.label == childThreeLabel}

        val completedState = CoroutineState.COMPLETED
        assertEquals(completedState, parent?.state)
        assertEquals(completedState, childOne?.state)
        assertEquals(completedState, childTwo?.state)
        assertEquals(completedState, childThree?.state)
        val eventLogExpected = listOf(parentLabel,childTwoLabel,childOneLabel,childThreeLabel)
        assertEquals(eventLog, eventLogExpected)

    }

    companion object{
        val logger = LoggerFactory.getLogger(VizLaunchTest::class.java)
    }
}