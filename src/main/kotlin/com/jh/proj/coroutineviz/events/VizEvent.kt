package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Base interface for all visualization events.
 * All events in the system must implement this interface.
 * 
 * Note: Not sealed to allow extension from subpackages.
 */
interface VizEvent {
    val sessionId: String
    val seq: Long
    val tsNanos: Long
    val kind: String
}

/**
 * Base interface for coroutine lifecycle events.
 * Events that track a specific coroutine's state changes.
 * 
 * Note: Not sealed to allow extension from subpackages.
 */
interface CoroutineEvent : VizEvent {
    val coroutineId: String
    val jobId: String
    val parentCoroutineId: String?
    val scopeId: String
    val label: String?
}
