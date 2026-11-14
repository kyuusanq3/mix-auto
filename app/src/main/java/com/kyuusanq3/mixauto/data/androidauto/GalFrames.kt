package com.kyuusanq3.mixauto.data.androidauto

/**
 * Android Auto control frames, from the published layout:
 * one channel byte, one flags byte, a big-endian body length, then a
 * big-endian message type and the message bytes.
 */
internal data class GalFrame(
    val channel: Int,
    val flags: Int,
    val messageType: Int,
    val payload: ByteArray,
)

internal object GalFrames {
    const val FLAG_FIRST = 0x01
    const val FLAG_LAST = 0x02
    const val FLAG_COMPLETE = FLAG_FIRST or FLAG_LAST
    const val CHANNEL_CONTROL = 0
    const val VERSION_REQUEST = 1
    const val VERSION_RESPONSE = 2
    const val SSL_HANDSHAKE = 3
    const val AUTH_COMPLETE = 4
    const val SERVICE_DISCOVERY_REQUEST = 5
    const val PING_REQUEST = 11
    const val PING_RESPONSE = 12
    const val SHUTDOWN_REQUEST = 15
    const val SHUTDOWN_RESPONSE = 16

    fun encode(
        channel: Int,
        messageType: Int,
        payload: ByteArray,
        flags: Int = FLAG_COMPLETE,
    ): ByteArray {
        val size = 2 + payload.size
        val out = ByteArray(4 + size)
        out[0] = channel.toByte()
        out[1] = flags.toByte()
        out[2] = (size shr 8).toByte()
        out[3] = (size and 0xff).toByte()
        out[4] = (messageType shr 8).toByte()
        out[5] = (messageType and 0xff).toByte()
        payload.copyInto(out, destinationOffset = 6)
        return out
    }

    fun decode(bytes: ByteArray): GalFrame? {
        if (bytes.size < 6) return null
        val size = ((bytes[2].toInt() and 0xff) shl 8) or (bytes[3].toInt() and 0xff)
        if (size < 2 || bytes.size < 4 + size) return null
        val type = ((bytes[4].toInt() and 0xff) shl 8) or (bytes[5].toInt() and 0xff)
        return GalFrame(
            channel = bytes[0].toInt() and 0xff,
            flags = bytes[1].toInt() and 0xff,
            messageType = type,
            payload = bytes.copyOfRange(6, 4 + size),
        )
    }
}
