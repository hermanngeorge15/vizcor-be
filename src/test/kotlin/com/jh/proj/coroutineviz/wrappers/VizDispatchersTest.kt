package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.dispatcher.DispatcherSelected
import com.jh.proj.coroutineviz.events.dispatcher.ThreadAssigned
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import java.util.concurrent.Executors

@DisplayName("VizDispatchers Tests")
class VizDispatchersTest {

    @Test
    @DisplayName("Should create VizDispatchers with default properties")
    fun testVizDispatchersCreation() {
        val session = VizSession("test-session")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        assertNotNull(dispatchers.default)
        assertNotNull(dispatchers.io)
        assertNotNull(dispatchers.unconfined)
        
        assertEquals("Dispatchers.Default", dispatchers.default.dispatcherName)
        assertEquals("Dispatchers.IO", dispatchers.io.dispatcherName)
        assertEquals("Dispatchers.Unconfined", dispatchers.unconfined.dispatcherName)
    }

    @Test
    @DisplayName("Should emit DispatcherSelected event when using default dispatcher")
    fun testDefaultDispatcherEmitsEvents() = runTest {
        val session = VizSession("test-default-dispatcher")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        val scope = VizScope(session, context = dispatchers.default)
        
        scope.vizLaunch(label = "test-coroutine") {
            vizDelay(100)
        }.join()
        
        // Verify events
        val events = session.store.all()
        val dispatcherEvents = events.filter { it.kind == "DispatcherSelected" }
        
        assertTrue(dispatcherEvents.isNotEmpty(), "Should have DispatcherSelected events")
        
        // Check that Default dispatcher was used
        val dispatcherSelectedEvents = dispatcherEvents.filterIsInstance<DispatcherSelected>()
        val hasDefaultDispatcher = dispatcherSelectedEvents.any { it.dispatcherName == "Dispatchers.Default" }
        assertTrue(hasDefaultDispatcher, "Should use Dispatchers.Default")
    }

    @Test
    @DisplayName("Should emit DispatcherSelected event when using IO dispatcher")
    fun testIODispatcherEmitsEvents() = runTest {
        val session = VizSession("test-io-dispatcher")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        val scope = VizScope(session, context = dispatchers.io)
        
        scope.vizLaunch(label = "io-coroutine") {
            vizDelay(100)
        }.join()
        
        // Verify events
        val events = session.store.all()
        val dispatcherEvents = events.filter { it.kind == "DispatcherSelected" }
        
        assertTrue(dispatcherEvents.isNotEmpty(), "Should have DispatcherSelected events")
        
        // Check that IO dispatcher was used
        val dispatcherSelectedEvents = dispatcherEvents.filterIsInstance<DispatcherSelected>()
        val hasIODispatcher = dispatcherSelectedEvents.any { it.dispatcherName == "Dispatchers.IO" }
        assertTrue(hasIODispatcher, "Should use Dispatchers.IO")
    }

    @Test
    @DisplayName("Should emit ThreadAssigned events with dispatcher names")
    fun testThreadAssignedEventsIncludeDispatcherName() = runTest {
        val session = VizSession("test-thread-assigned")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        val scope = VizScope(session, context = dispatchers.default)
        
        scope.vizLaunch(label = "thread-test") {
            vizDelay(100)
        }.join()
        
        // Verify ThreadAssigned events have dispatcher names
        val events = session.store.all()
        val threadEvents = events.filter { it.kind == "ThreadAssigned" }
        
        assertTrue(threadEvents.isNotEmpty(), "Should have ThreadAssigned events")
        
        // Check that thread events include dispatcher name
        val threadAssignedEvents = threadEvents.filterIsInstance<ThreadAssigned>()
        threadAssignedEvents.forEach { event ->
            assertTrue(
                event.dispatcherName != null,
                "ThreadAssigned event should include dispatcherName"
            )
        }
    }

    @Test
    @DisplayName("Should handle dispatcher switching in nested coroutines")
    fun testDispatcherSwitchingInNestedCoroutines() = runTest {
        val session = VizSession("test-dispatcher-switching")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        // Parent uses Default
        val scope = VizScope(session, context = dispatchers.default)
        
        scope.vizLaunch(label = "parent") {
            // Parent on Default
            
            // Child 1: stays on Default
            vizLaunch(label = "child-default") {
                vizDelay(50)
            }
            
            // Child 2: switches to IO
            vizLaunch(label = "child-io", context = dispatchers.io) {
                vizDelay(50)
            }
        }.join()
        
        // Verify both dispatchers were used
        val events = session.store.all()
        val dispatcherEvents = events.filterIsInstance<DispatcherSelected>()
        
        assertTrue(dispatcherEvents.size >= 2, "Should have multiple dispatcher events")
        
        val hasDefault = dispatcherEvents.any { it.dispatcherName == "Dispatchers.Default" }
        val hasIO = dispatcherEvents.any { it.dispatcherName == "Dispatchers.IO" }
        
        assertTrue(hasDefault, "Should have used Dispatchers.Default")
        assertTrue(hasIO, "Should have used Dispatchers.IO")
    }

    @Test
    @DisplayName("Should instrument custom dispatcher correctly")
    fun testCustomDispatcherInstrumentation() = runTest {
        val session = VizSession("test-custom-dispatcher")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        // Create custom dispatcher
        val customThreadPool = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        
        // Instrument it
        val instrumentedCustom = dispatchers.instrument(customThreadPool, "CustomPool")
        
        assertNotNull(instrumentedCustom)
        assertEquals("CustomPool", instrumentedCustom.dispatcherName)
        
        // Use it
        val scope = VizScope(session, context = instrumentedCustom)
        
        scope.vizLaunch(label = "custom-work") {
            vizDelay(100)
        }.join()
        
        // Verify custom dispatcher was tracked
        val events = session.store.all()
        val dispatcherEvents = events.filterIsInstance<DispatcherSelected>()
        
        assertTrue(dispatcherEvents.isNotEmpty(), "Should have dispatcher events")
        
        val hasCustom = dispatcherEvents.any { it.dispatcherName == "CustomPool" }
        
        assertTrue(hasCustom, "Should have used CustomPool dispatcher")
        
        // Cleanup
        customThreadPool.close()
    }

    @Test
    @DisplayName("Should not double-wrap already instrumented dispatcher")
    fun testAlreadyInstrumentedDispatcherNotDoubleWrapped() {
        val session = VizSession("test-double-wrap")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        val instrumented = dispatchers.default
        val reinstrumented = dispatchers.instrument(instrumented, "ShouldNotWrap")
        
        // Should return the same instance
        assertSame(instrumented, reinstrumented, "Should not double-wrap instrumented dispatcher")
    }

    @Test
    @DisplayName("Should create multiple VizDispatchers with different scope IDs")
    fun testMultipleVizDispatchersWithDifferentScopes() {
        val session = VizSession("test-multiple-dispatchers")
        
        val dispatchers1 = VizDispatchers(session, scopeId = "scope-1")
        val dispatchers2 = VizDispatchers(session, scopeId = "scope-2")
        
        // Should have different dispatcher IDs
        assertNotEquals(
            dispatchers1.default.dispatcherId,
            dispatchers2.default.dispatcherId,
            "Different scopes should have different dispatcher IDs"
        )
        
        // But same names
        assertEquals(
            dispatchers1.default.dispatcherName,
            dispatchers2.default.dispatcherName,
            "Same dispatcher type should have same name"
        )
    }

    @Test
    @DisplayName("Should work with vizAsync")
    fun testVizDispatchersWithVizAsync() = runTest {
        val session = VizSession("test-async-dispatcher")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        val scope = VizScope(session, context = dispatchers.io)
        
        val deferred = scope.vizAsync(label = "async-task") {
            vizDelay(100)
            "result"
        }
        
        val result = deferred.await()
        
        assertEquals("result", result)
        
        // Verify dispatcher events
        val events = session.store.all()
        val dispatcherEvents = events.filter { it.kind == "DispatcherSelected" }
        
        assertTrue(dispatcherEvents.isNotEmpty(), "Should have dispatcher events for async")
    }

    @Test
    @DisplayName("Should track multiple coroutines on same dispatcher")
    fun testMultipleCoroutinesOnSameDispatcher() = runTest {
        val session = VizSession("test-multiple-coroutines")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        val scope = VizScope(session, context = dispatchers.default)
        
        // Launch multiple coroutines
        val jobs = List(5) { index ->
            scope.vizLaunch(label = "worker-$index") {
                vizDelay(50)
            }
        }
        
        // Wait for all
        jobs.forEach { it.join() }
        
        // Verify all coroutines were tracked
        val events = session.store.all()
        val dispatcherEvents = events.filterIsInstance<DispatcherSelected>()
        
        // Each coroutine may emit multiple dispatcher events (creation, dispatch, etc.)
        assertTrue(dispatcherEvents.size >= 5, "Should have at least 5 dispatcher events, got ${dispatcherEvents.size}")
    }

    @Test
    @DisplayName("Should handle unconfined dispatcher")
    fun testUnconfinedDispatcher() = runBlocking {
        val session = VizSession("test-unconfined")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        // Note: Dispatchers.Unconfined doesn't dispatch, so we won't get DispatcherSelected events
        // This test verifies that using unconfined dispatcher doesn't cause errors
        val scope = VizScope(session, context = dispatchers.unconfined)
        
        scope.vizLaunch(label = "unconfined-work") {
            delay(100)
        }.join()
        
        // Verify the scope was created and coroutine ran without errors
        val events = session.store.all()
        assertTrue(events.isNotEmpty(), "Should have some events even with Unconfined dispatcher")
        
        // Verify we can access unconfined dispatcher properties
        assertEquals("Dispatchers.Unconfined", dispatchers.unconfined.dispatcherName)
    }

    @Test
    @DisplayName("Should create VizDispatchers with custom scope ID")
    fun testCustomScopeId() {
        val session = VizSession("test-factory")
        val dispatchers = VizDispatchers(session, scopeId = "custom-scope")
        
        assertNotNull(dispatchers)
        assertNotNull(dispatchers.default)
        assertNotNull(dispatchers.io)
        
        // Verify scope ID is used in dispatcher IDs
        assertTrue(dispatchers.default.dispatcherId.contains("custom-scope"))
        assertTrue(dispatchers.io.dispatcherId.contains("custom-scope"))
    }

    @Test
    @DisplayName("Should generate unique dispatcher IDs for same scope")
    fun testUniqueDispatcherIDs() {
        val session = VizSession("test-unique-ids")
        val dispatchers = VizDispatchers(session, scopeId = "test")
        
        // All dispatchers in the same VizDispatchers should have unique IDs
        val defaultId = dispatchers.default.dispatcherId
        val ioId = dispatchers.io.dispatcherId
        val unconfinedId = dispatchers.unconfined.dispatcherId
        
        assertNotEquals(defaultId, ioId, "Default and IO should have different IDs")
        assertNotEquals(defaultId, unconfinedId, "Default and Unconfined should have different IDs")
        assertNotEquals(ioId, unconfinedId, "IO and Unconfined should have different IDs")
    }
}

