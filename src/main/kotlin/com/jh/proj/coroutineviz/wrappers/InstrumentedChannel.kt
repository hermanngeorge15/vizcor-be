@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jh.proj.coroutineviz.wrappers

import com.jh.proj.coroutineviz.events.*
import com.jh.proj.coroutineviz.session.VizSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.selects.SelectClause1
import kotlinx.coroutines.selects.SelectClause2
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * InstrumentedChannel wraps a Channel to emit visualization events.
 * 
 * Features:
 * - Tracks all send/receive operations
 * - Monitors buffer state changes
 * - Detects suspension due to backpressure
 * - Tracks channel close and cancellation
 * - Supports all channel capacities (rendezvous, buffered, unlimited, conflated)
 * 
 * @param E The type of elements in the channel
 * @param delegate The underlying Channel to wrap
 * @param session The VizSession for event tracking
 * @param channelId Unique identifier for this channel
 * @param label Optional human-readable label
 * @param capacity The channel capacity
 * @param onBufferOverflow The overflow strategy
 */
class InstrumentedChannel<E>(
    private val delegate: Channel<E>,
    private val session: VizSession,
    val channelId: String,
    val label: String? = null,
    private val capacity: Int,
    private val onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) : Channel<E> by delegate {

    private val totalSent = AtomicLong(0)
    private val totalReceived = AtomicLong(0)
    private val currentBufferSize = AtomicInteger(0)
    private val pendingSenders = AtomicInteger(0)
    private val pendingReceivers = AtomicInteger(0)

    init {
        val capacityName = when (capacity) {
            Channel.RENDEZVOUS -> "RENDEZVOUS"
            Channel.UNLIMITED -> "UNLIMITED"
            Channel.CONFLATED -> "CONFLATED"
            Channel.BUFFERED -> "BUFFERED"
            else -> "BUFFERED($capacity)"
        }
        
        session.send(
            ChannelCreated(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                channelId = channelId,
                capacity = capacity,
                capacityName = capacityName,
                onBufferOverflow = onBufferOverflow.name,
                onUndeliveredElement = false,
                label = label,
                scopeId = null,
                coroutineId = null
            )
        )
        
        logger.debug("Channel created: channelId=$channelId, capacity=$capacityName, overflow=${onBufferOverflow.name}")
    }

    // ========================================================================
    // SendChannel Implementation (override delegation)
    // ========================================================================

    override suspend fun send(element: E) {
        val senderId = "send-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        val bufferBefore = currentBufferSize.get()
        
        // Emit send started
        session.send(
            ChannelSendStarted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = startTime,
                channelId = channelId,
                senderId = senderId,
                coroutineId = coroutineId,
                valuePreview = element?.toString()?.take(200) ?: "null",
                valueType = element?.javaClass?.simpleName ?: "null",
                bufferSize = bufferBefore,
                bufferCapacity = capacity,
                label = label
            )
        )

        var wasSuspended = false
        
        try {
            // Check if we'll suspend
            if (capacity != Channel.UNLIMITED && 
                bufferBefore >= capacity && 
                capacity != Channel.CONFLATED &&
                capacity > 0 &&
                onBufferOverflow == BufferOverflow.SUSPEND) {
                
                wasSuspended = true
                pendingSenders.incrementAndGet()
                
                session.send(
                    ChannelSendSuspended(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        channelId = channelId,
                        senderId = senderId,
                        coroutineId = coroutineId,
                        reason = "buffer_full",
                        bufferSize = bufferBefore,
                        bufferCapacity = capacity,
                        pendingSenders = pendingSenders.get(),
                        label = label
                    )
                )
            }

            delegate.send(element)
            
            totalSent.incrementAndGet()
            if (capacity != Channel.UNLIMITED && capacity != Channel.CONFLATED) {
                currentBufferSize.incrementAndGet()
            }
            
            if (wasSuspended) {
                pendingSenders.decrementAndGet()
            }

            val endTime = System.nanoTime()
            
            session.send(
                ChannelSendCompleted(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = endTime,
                    channelId = channelId,
                    senderId = senderId,
                    coroutineId = coroutineId,
                    valuePreview = element?.toString()?.take(200) ?: "null",
                    durationNanos = endTime - startTime,
                    wasSuspended = wasSuspended,
                    bufferSize = currentBufferSize.get(),
                    label = label
                )
            )

            logger.trace("Channel send completed: channelId=$channelId, value=$element, suspended=$wasSuspended")

        } catch (e: ClosedSendChannelException) {
            if (wasSuspended) pendingSenders.decrementAndGet()
            throw e
        } catch (e: CancellationException) {
            if (wasSuspended) pendingSenders.decrementAndGet()
            throw e
        }
    }

    override fun trySend(element: E): ChannelResult<Unit> {
        val senderId = "trySend-${session.nextSeq()}"
        val coroutineId = runCatching { 
            kotlinx.coroutines.runBlocking { 
                currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
            } 
        }.getOrNull() ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(
            ChannelSendStarted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = startTime,
                channelId = channelId,
                senderId = senderId,
                coroutineId = coroutineId,
                valuePreview = element?.toString()?.take(200) ?: "null",
                valueType = element?.javaClass?.simpleName ?: "null",
                bufferSize = currentBufferSize.get(),
                bufferCapacity = capacity,
                label = label
            )
        )

        val result = delegate.trySend(element)
        
        if (result.isSuccess) {
            totalSent.incrementAndGet()
            if (capacity != Channel.UNLIMITED && capacity != Channel.CONFLATED) {
                currentBufferSize.incrementAndGet()
            }
            
            session.send(
                ChannelSendCompleted(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    channelId = channelId,
                    senderId = senderId,
                    coroutineId = coroutineId,
                    valuePreview = element?.toString()?.take(200) ?: "null",
                    durationNanos = System.nanoTime() - startTime,
                    wasSuspended = false,
                    bufferSize = currentBufferSize.get(),
                    label = label
                )
            )
        } else if (result.isFailure) {
            // Buffer overflow or closed
            val exception = result.exceptionOrNull()
            if (exception !is ClosedSendChannelException) {
                // Buffer overflow
                session.send(
                    ChannelBufferOverflow(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        channelId = channelId,
                        coroutineId = coroutineId,
                        droppedValuePreview = element?.toString()?.take(200),
                        droppedValueType = element?.javaClass?.simpleName,
                        strategy = "TRYSEND_FAILED",
                        bufferSize = currentBufferSize.get(),
                        bufferCapacity = capacity,
                        label = label
                    )
                )
            }
        }

        return result
    }

    override fun close(cause: Throwable?): Boolean {
        val coroutineId = runCatching { 
            kotlinx.coroutines.runBlocking { 
                currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
            } 
        }.getOrNull()
        
        val result = delegate.close(cause)
        
        if (result) {
            session.send(
                ChannelClosed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    channelId = channelId,
                    coroutineId = coroutineId,
                    cause = cause?.message,
                    totalSent = totalSent.get(),
                    totalReceived = totalReceived.get(),
                    remainingInBuffer = currentBufferSize.get(),
                    label = label
                )
            )
            
            logger.debug("Channel closed: channelId=$channelId, sent=${totalSent.get()}, received=${totalReceived.get()}")
        }
        
        return result
    }

    // ========================================================================
    // ReceiveChannel Implementation (override delegation)
    // ========================================================================

    override suspend fun receive(): E {
        val receiverId = "recv-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        val bufferBefore = currentBufferSize.get()
        
        // Emit receive started
        session.send(
            ChannelReceiveStarted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = startTime,
                channelId = channelId,
                receiverId = receiverId,
                coroutineId = coroutineId,
                bufferSize = bufferBefore,
                label = label
            )
        )

        var wasSuspended = false
        
        try {
            // Check if we'll suspend (buffer empty for buffered channels)
            if (bufferBefore == 0 && capacity != Channel.CONFLATED) {
                wasSuspended = true
                pendingReceivers.incrementAndGet()
                
                session.send(
                    ChannelReceiveSuspended(
                        sessionId = session.sessionId,
                        seq = session.nextSeq(),
                        tsNanos = System.nanoTime(),
                        channelId = channelId,
                        receiverId = receiverId,
                        coroutineId = coroutineId,
                        reason = "buffer_empty",
                        pendingReceivers = pendingReceivers.get(),
                        label = label
                    )
                )
            }

            val element = delegate.receive()
            
            totalReceived.incrementAndGet()
            if (capacity != Channel.UNLIMITED && capacity != Channel.CONFLATED) {
                val newSize = currentBufferSize.decrementAndGet()
                if (newSize < 0) currentBufferSize.set(0)
            }
            
            if (wasSuspended) {
                pendingReceivers.decrementAndGet()
            }

            val endTime = System.nanoTime()
            
            session.send(
                ChannelReceiveCompleted(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = endTime,
                    channelId = channelId,
                    receiverId = receiverId,
                    coroutineId = coroutineId,
                    valuePreview = element?.toString()?.take(200) ?: "null",
                    valueType = element?.javaClass?.simpleName ?: "null",
                    durationNanos = endTime - startTime,
                    wasSuspended = wasSuspended,
                    bufferSize = currentBufferSize.get(),
                    label = label
                )
            )

            logger.trace("Channel receive completed: channelId=$channelId, value=$element, suspended=$wasSuspended")
            
            return element

        } catch (e: ClosedReceiveChannelException) {
            if (wasSuspended) pendingReceivers.decrementAndGet()
            
            session.send(
                ChannelReceiveFailed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    channelId = channelId,
                    receiverId = receiverId,
                    coroutineId = coroutineId,
                    reason = "channel_closed",
                    cause = e.message,
                    label = label
                )
            )
            
            throw e
        } catch (e: CancellationException) {
            if (wasSuspended) pendingReceivers.decrementAndGet()
            
            session.send(
                ChannelReceiveFailed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    channelId = channelId,
                    receiverId = receiverId,
                    coroutineId = coroutineId,
                    reason = "cancelled",
                    cause = e.message,
                    label = label
                )
            )
            
            throw e
        }
    }

    override suspend fun receiveCatching(): ChannelResult<E> {
        val receiverId = "recvCatch-${session.nextSeq()}"
        val coroutineId = currentCoroutineContext()[VizCoroutineElement]?.coroutineId ?: "unknown"
        val startTime = System.nanoTime()
        
        session.send(
            ChannelReceiveStarted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = startTime,
                channelId = channelId,
                receiverId = receiverId,
                coroutineId = coroutineId,
                bufferSize = currentBufferSize.get(),
                label = label
            )
        )

        val result = delegate.receiveCatching()
        
        if (result.isSuccess) {
            val element = result.getOrNull()
            totalReceived.incrementAndGet()
            if (capacity != Channel.UNLIMITED && capacity != Channel.CONFLATED) {
                val newSize = currentBufferSize.decrementAndGet()
                if (newSize < 0) currentBufferSize.set(0)
            }
            
            session.send(
                ChannelReceiveCompleted(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    channelId = channelId,
                    receiverId = receiverId,
                    coroutineId = coroutineId,
                    valuePreview = element?.toString()?.take(200) ?: "null",
                    valueType = element?.javaClass?.simpleName ?: "null",
                    durationNanos = System.nanoTime() - startTime,
                    wasSuspended = false,
                    bufferSize = currentBufferSize.get(),
                    label = label
                )
            )
        } else if (result.isClosed) {
            session.send(
                ChannelReceiveFailed(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    channelId = channelId,
                    receiverId = receiverId,
                    coroutineId = coroutineId,
                    reason = "channel_closed",
                    cause = result.exceptionOrNull()?.message,
                    label = label
                )
            )
        }

        return result
    }

    override fun tryReceive(): ChannelResult<E> {
        val receiverId = "tryRecv-${session.nextSeq()}"
        val coroutineId = runCatching { 
            kotlinx.coroutines.runBlocking { 
                currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
            } 
        }.getOrNull() ?: "unknown"
        
        session.send(
            ChannelReceiveStarted(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                channelId = channelId,
                receiverId = receiverId,
                coroutineId = coroutineId,
                bufferSize = currentBufferSize.get(),
                label = label
            )
        )

        val result = delegate.tryReceive()
        
        if (result.isSuccess) {
            val element = result.getOrNull()
            totalReceived.incrementAndGet()
            if (capacity != Channel.UNLIMITED && capacity != Channel.CONFLATED) {
                val newSize = currentBufferSize.decrementAndGet()
                if (newSize < 0) currentBufferSize.set(0)
            }
            
            session.send(
                ChannelReceiveCompleted(
                    sessionId = session.sessionId,
                    seq = session.nextSeq(),
                    tsNanos = System.nanoTime(),
                    channelId = channelId,
                    receiverId = receiverId,
                    coroutineId = coroutineId,
                    valuePreview = element?.toString()?.take(200) ?: "null",
                    valueType = element?.javaClass?.simpleName ?: "null",
                    durationNanos = 0,
                    wasSuspended = false,
                    bufferSize = currentBufferSize.get(),
                    label = label
                )
            )
        }

        return result
    }

    override fun cancel(cause: CancellationException?) {
        val coroutineId = runCatching { 
            kotlinx.coroutines.runBlocking { 
                currentCoroutineContext()[VizCoroutineElement]?.coroutineId 
            } 
        }.getOrNull()
        
        session.send(
            ChannelClosed(
                sessionId = session.sessionId,
                seq = session.nextSeq(),
                tsNanos = System.nanoTime(),
                channelId = channelId,
                coroutineId = coroutineId,
                cause = cause?.message ?: "cancelled",
                totalSent = totalSent.get(),
                totalReceived = totalReceived.get(),
                remainingInBuffer = currentBufferSize.get(),
                label = label
            )
        )
        
        delegate.cancel(cause)
        
        logger.debug("Channel cancelled: channelId=$channelId")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstrumentedChannel::class.java)
    }
}

// ============================================================================
// Extension Functions
// ============================================================================

/**
 * Create an instrumented channel with the given capacity.
 */
fun <E> VizSession.vizChannel(
    capacity: Int = Channel.RENDEZVOUS,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    label: String? = null
): InstrumentedChannel<E> {
    val channelId = "channel-${nextSeq()}"
    return InstrumentedChannel(
        delegate = Channel(capacity, onBufferOverflow),
        session = this,
        channelId = channelId,
        label = label,
        capacity = capacity,
        onBufferOverflow = onBufferOverflow
    )
}

/**
 * Create an instrumented rendezvous channel.
 */
fun <E> VizSession.vizRendezvousChannel(
    label: String? = null
): InstrumentedChannel<E> = vizChannel(Channel.RENDEZVOUS, label = label)

/**
 * Create an instrumented unlimited channel.
 */
fun <E> VizSession.vizUnlimitedChannel(
    label: String? = null
): InstrumentedChannel<E> = vizChannel(Channel.UNLIMITED, label = label)

/**
 * Create an instrumented conflated channel.
 */
fun <E> VizSession.vizConflatedChannel(
    label: String? = null
): InstrumentedChannel<E> = vizChannel(Channel.CONFLATED, label = label)

/**
 * Create an instrumented buffered channel with default capacity.
 */
fun <E> VizSession.vizBufferedChannel(
    label: String? = null
): InstrumentedChannel<E> = vizChannel(Channel.BUFFERED, label = label)

/**
 * Wrap an existing channel with instrumentation.
 */
fun <E> Channel<E>.instrumented(
    session: VizSession,
    capacity: Int = Channel.RENDEZVOUS,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    label: String? = null
): InstrumentedChannel<E> {
    val channelId = "channel-${session.nextSeq()}"
    return InstrumentedChannel(
        delegate = this,
        session = session,
        channelId = channelId,
        label = label,
        capacity = capacity,
        onBufferOverflow = onBufferOverflow
    )
}
