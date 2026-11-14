package com.kyuusanq3.mixauto.data.androidauto

import org.junit.Assert.assertEquals
import org.junit.Test

class GalFramesTest {
    @Test
    fun versionRequestRoundTrips() {
        val encoded = GalFrames.encode(
            channel = GalFrames.CHANNEL_CONTROL,
            messageType = GalFrames.VERSION_REQUEST,
            payload = shortBytes(1, 6),
        )
        val decoded = GalFrames.decode(encoded)

        assertEquals(GalFrames.CHANNEL_CONTROL, decoded?.channel)
        assertEquals(GalFrames.FLAG_COMPLETE, decoded?.flags)
        assertEquals(GalFrames.VERSION_REQUEST, decoded?.messageType)
        assertEquals(listOf(1, 6), shortsOf(decoded?.payload ?: byteArrayOf()))
    }
}
