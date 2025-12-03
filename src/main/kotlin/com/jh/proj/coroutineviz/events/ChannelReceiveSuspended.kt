package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a receive operation suspends (buffer empty, no sender ready).
 */
@Serializable
data class ChannelReceiveSuspended(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val receiverId: String,
    val coroutineId: String,
    val reason: String,  // "buffer_empty", "no_sender"
    val pendingReceivers: Int,  // Number of coroutines waiting to receive
    val label: String? = null
) : VizEvent {
    override val kind = "ChannelReceiveSuspended"
}

