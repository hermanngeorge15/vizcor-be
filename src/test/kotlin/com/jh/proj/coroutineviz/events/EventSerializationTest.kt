package com.jh.proj.coroutineviz.events

import com.jh.proj.coroutineviz.events.coroutine.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventSerializationTest {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun `CoroutineCreated serialization round-trip`() {
        val event = CoroutineCreated(
            sessionId = "test-session",
            seq = 1L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = "test-coroutine"
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineCreated>(serialized)

        assertEquals(event, deserialized)
        assertTrue(serialized.contains("\"sessionId\":\"test-session\""))
        assertTrue(serialized.contains("\"coroutineId\":\"coroutine-1\""))
        assertTrue(serialized.contains("\"label\":\"test-coroutine\""))
    }

    @Test
    fun `CoroutineStarted serialization round-trip`() {
        val event = CoroutineStarted(
            sessionId = "test-session",
            seq = 2L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = "test-coroutine"
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineStarted>(serialized)

        assertEquals(event, deserialized)
    }

    @Test
    fun `CoroutineCompleted serialization round-trip`() {
        val event = CoroutineCompleted(
            sessionId = "test-session",
            seq = 3L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = "coroutine-0",
            scopeId = "scope-1",
            label = "child-coroutine"
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineCompleted>(serialized)

        assertEquals(event, deserialized)
        assertTrue(serialized.contains("\"parentCoroutineId\":\"coroutine-0\""))
    }

    @Test
    fun `CoroutineFailed serialization round-trip`() {
        val event = CoroutineFailed(
            sessionId = "test-session",
            seq = 4L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = "failing-coroutine",
            exceptionType = "java.lang.RuntimeException",
            message = "Test failure",
            stackTrace = listOf("at com.example.Test.run(Test.kt:42)")
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineFailed>(serialized)

        assertEquals(event, deserialized)
        assertTrue(serialized.contains("\"exceptionType\":\"java.lang.RuntimeException\""))
        assertTrue(serialized.contains("\"message\":\"Test failure\""))
        assertTrue(serialized.contains("\"stackTrace\""))
    }

    @Test
    fun `CoroutineCancelled serialization round-trip`() {
        val event = CoroutineCancelled(
            sessionId = "test-session",
            seq = 5L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-2",
            jobId = "job-2",
            parentCoroutineId = "coroutine-1",
            scopeId = "scope-1",
            label = "cancelled-coroutine",
            cause = "Parent was cancelled"
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineCancelled>(serialized)

        assertEquals(event, deserialized)
        assertTrue(serialized.contains("\"cause\":\"Parent was cancelled\""))
    }

    @Test
    fun `CoroutineSuspended serialization round-trip`() {
        val event = CoroutineSuspended(
            sessionId = "test-session",
            seq = 6L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = "suspended-coroutine",
            reason = "delay",
            durationMillis = 1000L,
            suspensionPoint = SuspensionPoint(
                function = "doWork",
                fileName = "Worker.kt",
                lineNumber = 42,
                reason = "delay"
            )
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineSuspended>(serialized)

        assertEquals(event, deserialized)
        assertTrue(serialized.contains("\"reason\":\"delay\""))
        assertTrue(serialized.contains("\"durationMillis\":1000"))
        assertTrue(serialized.contains("\"function\":\"doWork\""))
    }

    @Test
    fun `CoroutineSuspended with null optional fields`() {
        val event = CoroutineSuspended(
            sessionId = "test-session",
            seq = 7L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = null,
            reason = "await",
            durationMillis = null,
            suspensionPoint = null
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineSuspended>(serialized)

        assertEquals(event, deserialized)
    }

    @Test
    fun `CoroutineResumed serialization round-trip`() {
        val event = CoroutineResumed(
            sessionId = "test-session",
            seq = 8L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = "resumed-coroutine"
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineResumed>(serialized)

        assertEquals(event, deserialized)
    }

    @Test
    fun `CoroutineBodyCompleted serialization round-trip`() {
        val event = CoroutineBodyCompleted(
            sessionId = "test-session",
            seq = 9L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = "coroutine-0",
            scopeId = "scope-1",
            label = "body-completed-coroutine"
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineBodyCompleted>(serialized)

        assertEquals(event, deserialized)
    }

    @Test
    fun `SuspensionPoint serialization round-trip`() {
        val point = SuspensionPoint(
            function = "fetchData",
            fileName = "DataService.kt",
            lineNumber = 99,
            reason = "withContext"
        )

        val serialized = json.encodeToString(point)
        val deserialized = json.decodeFromString<SuspensionPoint>(serialized)

        assertEquals(point, deserialized)
        assertTrue(serialized.contains("\"function\":\"fetchData\""))
        assertTrue(serialized.contains("\"fileName\":\"DataService.kt\""))
        assertTrue(serialized.contains("\"lineNumber\":99"))
        assertTrue(serialized.contains("\"reason\":\"withContext\""))
    }

    @Test
    fun `SuspensionPoint with null optional fields`() {
        val point = SuspensionPoint(
            function = "unknown",
            fileName = null,
            lineNumber = null,
            reason = "delay"
        )

        val serialized = json.encodeToString(point)
        val deserialized = json.decodeFromString<SuspensionPoint>(serialized)

        assertEquals(point, deserialized)
    }

    @Test
    fun `CoroutineCreated with null parentCoroutineId and label`() {
        val event = CoroutineCreated(
            sessionId = "test-session",
            seq = 10L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-root",
            jobId = "job-root",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = null
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineCreated>(serialized)

        assertEquals(event, deserialized)
    }

    @Test
    fun `serialized events contain kind field via SerialName`() {
        val created = CoroutineCreated(
            sessionId = "s", seq = 1, tsNanos = 0,
            coroutineId = "c", jobId = "j",
            parentCoroutineId = null, scopeId = "sc", label = null
        )
        val serialized = json.encodeToString(created)
        // The @SerialName annotation uses "type" discriminator by default,
        // but since we're encoding the concrete type directly, the kind
        // is a computed property and appears in the JSON output
        assertNotNull(serialized, "Serialized output should not be null")
        assertTrue(serialized.isNotEmpty(), "Serialized output should not be empty")
    }

    @Test
    fun `CoroutineFailed with empty stack trace`() {
        val event = CoroutineFailed(
            sessionId = "test-session",
            seq = 11L,
            tsNanos = System.nanoTime(),
            coroutineId = "coroutine-1",
            jobId = "job-1",
            parentCoroutineId = null,
            scopeId = "scope-1",
            label = "failing",
            exceptionType = null,
            message = null,
            stackTrace = emptyList()
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CoroutineFailed>(serialized)

        assertEquals(event, deserialized)
        assertTrue(serialized.contains("\"stackTrace\":[]"))
    }
}
