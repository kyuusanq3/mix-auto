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
    fun parseData3d4dPrefersPrecisePlaceCoords() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(
            "https://www.google.com/maps/place/Abbraccio/@-15.7202756,-47.9207687,13z/data=" +
                "!4m9!8m2!3d-15.7202756!4d-47.8857498",
        )
        assertNotNull(result)
        assertEquals(-15.7202756, result!!.lat, 0.0001)
        assertEquals(-47.8857498, result.lng, 0.0001)
    }

    @Test
    fun parseLlParameter() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(
            "https://www.google.com/maps/d/viewer?mid=1X&ll=10.67,122.95&z=15",
        )
        assertNotNull(result)
        assertEquals(10.67, result!!.lat, 0.0001)
        assertEquals(122.95, result.lng, 0.0001)
    }

    @Test
    fun parsePlaceIdRedirectUrlExtractsNameOnlyWithoutBody() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(
            "https://www.google.com/maps/place/Upstar+Variety+Store,+Bacolod/data=" +
                "!4m2!3m1!1s0x33aed179998a4a2f:0xd8ebe45701913e62",
        )
        assertNull(result)
    }

    @Test
    fun parsePreviewBodyWithEncodedProtobufCoords() {
        val bodySnippet = """
            <link href="/maps/preview/place?q=Upstar+Variety+Store&amp;pb=
            %211m3%211d15683%212d123.000084%213d10.6654242%212m3" rel="preload">
        """.trimIndent()
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(bodySnippet)
        assertNotNull(result)
        assertEquals(10.6654242, result!!.lat, 0.0001)
        assertEquals(123.000084, result.lng, 0.0001)
    }

    @Test
    fun parsePlaceIdRedirectUrlWithNameInPath() {
        val url = "https://www.google.com/maps/place/Upstar+Variety+Store,+Bacolod/data=" +
            "!4m2!3m1!1s0x33aed179998a4a2f:0xd8ebe45701913e62!8m2!3d10.6654242!4d123.000084"
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(url)
        assertNotNull(result)
        assertEquals(10.6654242, result!!.lat, 0.0001)
        assertEquals(123.000084, result.lng, 0.0001)
        assertEquals("Upstar Variety Store, Bacolod", result.suggestedName)
    }

    @Test
    fun parseInvalidReturnsNull() {
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("not a link"))
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("https://www.google.com/maps"))
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("91.0, 200.0"))
    }
}
