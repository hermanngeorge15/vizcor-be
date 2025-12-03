package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a receive operation fails (channel closed, cancelled, etc.).
 */
@Serializable
data class ChannelReceiveFailed(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val receiverId: String,
    val coroutineId: String,
    val reason: String,  // "channel_closed", "cancelled", "channel_failed"
    val cause: String?,  // Exception message
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelReceiveFailed"
}

