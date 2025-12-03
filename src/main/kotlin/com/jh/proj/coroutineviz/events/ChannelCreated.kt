package com.jh.proj.coroutineviz.events

import kotlinx.serialization.Serializable

/**
 * Emitted when a Channel is created.
 */
@Serializable
data class ChannelCreated(
    override val sessionId: String,
    override val seq: Long,
    override val tsNanos: Long,
    val channelId: String,
    val capacity: Int,  // Channel.RENDEZVOUS (0), Channel.UNLIMITED, Channel.CONFLATED (-1), Channel.BUFFERED (64), or custom
    val capacityName: String,  // "RENDEZVOUS", "UNLIMITED", "CONFLATED", "BUFFERED", or "BUFFERED(n)"
    val onBufferOverflow: String,  // "SUSPEND", "DROP_OLDEST", "DROP_LATEST"
    val onUndeliveredElement: Boolean,  // Whether onUndeliveredElement handler is set
    val label: String? = null,
    val scopeId: String? = null,
    val coroutineId: String? = null
) : VizEvent {
    override val kind = "ChannelCreated"
}

