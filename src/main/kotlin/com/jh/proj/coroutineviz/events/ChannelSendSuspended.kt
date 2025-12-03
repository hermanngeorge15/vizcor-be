package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a send operation suspends (buffer full, no receiver ready).
 */
@Serializable
data class ChannelSendSuspended(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val senderId: String,
    val coroutineId: String,
    val reason: String,  // "buffer_full", "no_receiver"
    val bufferSize: Int,
    val bufferCapacity: Int,
    val pendingSenders: Int,  // Number of coroutines waiting to send
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelSendSuspended"
}

