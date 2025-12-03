package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a Channel is closed.
 */
@Serializable
data class ChannelClosed(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val coroutineId: String?,  // Coroutine that closed the channel (if known)
    val cause: String?,  // Exception message if closed with cause
    val totalSent: Long,  // Total elements sent to channel
    val totalReceived: Long,  // Total elements received from channel
    val remainingInBuffer: Int,  // Elements still in buffer when closed
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelClosed"
}

