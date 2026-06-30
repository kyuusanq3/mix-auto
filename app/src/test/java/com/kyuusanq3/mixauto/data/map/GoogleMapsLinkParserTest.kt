package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsLinkParserTest {

    @Test
    fun parseAtCoordinatesFromPlaceUrl() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(
            "https://www.google.com/maps/place/SM+City+Bacolod/@10.6778,122.9510,17z/data=abc",
        )
        assertNotNull(result)
        assertEquals(10.6778, result!!.lat, 0.0001)
        assertEquals(122.9510, result.lng, 0.0001)
        assertEquals("SM City Bacolod", result.suggestedName)
    }

    @Test
    fun parseQueryCoordinates() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(
            "https://www.google.com/maps?q=10.67,122.95",
        )
        assertNotNull(result)
        assertEquals(10.67, result!!.lat, 0.0001)
        assertEquals(122.95, result.lng, 0.0001)
    }

    @Test
    fun parseGeoUri() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText("geo:10.67,122.95")
        assertNotNull(result)
        assertEquals(10.67, result!!.lat, 0.0001)
        assertEquals(122.95, result.lng, 0.0001)
    }

    @Test
    fun parsePlainCoordinates() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText("10.67, 122.95")
        assertNotNull(result)
        assertEquals(10.67, result!!.lat, 0.0001)
        assertEquals(122.95, result.lng, 0.0001)
    }

    @Test
    fun parseInvalidReturnsNull() {
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("not a link"))
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("https://www.google.com/maps"))
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("91.0, 200.0"))
    }
}
