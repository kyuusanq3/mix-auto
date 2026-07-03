package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsLinkParserTest {

    @Test
    fun parsePlaceUrlPrefersProtobufPinOverAtViewport() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(
            "https://www.google.com/maps/place/Abarrotes+%22La+Escondida%22/" +
                "@28.6839691,-106.1021991,196m/data=!3m1!1e3!4m5!3m4!1s0x86ea43ae490dc3cd:0xabf595a99559d5d5" +
                "!8m2!3d28.6839087!4d-106.1027089",
        )
        assertNotNull(result)
        assertEquals(28.6839087, result!!.lat, 0.0001)
        assertEquals(-106.1027089, result.lng, 0.0001)
    }

    @Test
    fun pickBestPrefersBodyProtobufWhenUrlOnlyHasViewportAt() {
        val redirectUrl = "https://www.google.com/maps/place/Upstar+Variety+Store,+Bacolod/" +
            "@10.6778,122.9510,17z/data=!4m2!3m1!1s0x33aed179998a4a2f:0xd8ebe45701913e62"
        val bodySnippet = """
            <link href="/maps/preview/place?q=Upstar+Variety+Store&amp;pb=
            %211m3%211d15683%212d123.000084%213d10.6654242%212m3" rel="preload">
        """.trimIndent()
        val fromUrl = GoogleMapsLinkParser.parseCoordinatesFromText(redirectUrl)
        val fromBody = GoogleMapsLinkParser.parseCoordinatesFromText(bodySnippet)
        assertNull(fromUrl)
        assertNotNull(fromBody)

        val picked = GoogleMapsLinkParser.pickBestParsedCoordinates(
            fromUrl = fromUrl,
            fromBody = fromBody,
            urlText = redirectUrl,
            bodyText = bodySnippet,
        )
        assertNotNull(picked)
        assertEquals(10.6654242, picked!!.lat, 0.0001)
        assertEquals(123.000084, picked.lng, 0.0001)
    }

    @Test
    fun pickBestPrefersUrlProtobufOverBodyViewport() {
        val redirectUrl = "https://www.google.com/maps/place/Bacolod+Baywalk+Recreational+Park/" +
            "@10.6717781,122.941494,1072m/data=!3m1!1e3!4m6!3m5!1s0x33aed04d80c6a75d:0x114a82b8432fd88e" +
            "!8m2!3d10.6723916!4d122.9406367!16s/g/1hhxkh3ff"
        val bodySnippet = "pb=%211m3%211d4668%212d122.941494%213d10.6717781%214m2%213d10.6723916%214d122.9406367"
        val fromUrl = GoogleMapsLinkParser.parseCoordinatesFromText(redirectUrl)
        val fromBody = GoogleMapsLinkParser.parseCoordinatesFromText(bodySnippet)
        assertNotNull(fromUrl)
        assertNotNull(fromBody)

        val picked = GoogleMapsLinkParser.pickBestParsedCoordinates(
            fromUrl = fromUrl,
            fromBody = fromBody,
            urlText = redirectUrl,
            bodyText = bodySnippet,
        )
        assertEquals(10.6723916, picked!!.lat, 0.0001)
        assertEquals(122.9406367, picked.lng, 0.0001)
    }

    @Test
    fun parseBacolodBaywalkFullUrlUsesLastPlacePinNotViewportOrEarlierStop() {
        val fullUrl = "https://www.google.com/maps/place/Bacolod+Baywalk+Recreational+Park/" +
            "@10.6717781,122.941494,1072m/data=!3m1!1e3!4m14!1m7!3m6!1s0x33aed1000c378bb7:0xd82b0ae3cf3ee573" +
            "!2sSM+CIty+Bacolod+Northwing!8m2!3d10.6717781!4d122.9440743!16s%2Fg%2F11ld3zt32c!3m5" +
            "!1s0x33aed04d80c6a75d:0x114a82b8432fd88e!8m2!3d10.6723916!4d122.9406367!16s%2Fg%2F1hhxkh3ff?entry=ttu"
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(fullUrl)
        assertNotNull(result)
        assertEquals(10.6723916, result!!.lat, 0.0001)
        assertEquals(122.9406367, result.lng, 0.0001)
        assertEquals("Bacolod Baywalk Recreational Park", result.suggestedName)
    }

    @Test
    fun parsePlaceIdOnlyUrlSkipsViewportAtCoords() {
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(
            "https://www.google.com/maps/place/Upstar+Variety+Store,+Bacolod/" +
                "@10.6778,122.9510,17z/data=!4m2!3m1!1s0x33aed179998a4a2f:0xd8ebe45701913e62",
        )
        assertNull(result)
    }

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
        assertEquals("Upstar Variety Store", result.suggestedName)
    }

    @Test
    fun parsePlaceNameStripsFullAddressFromPath() {
        val url = "https://www.google.com/maps/place/" +
            "Upstar+Variety+Store,+Do%C3%B1a+Juliana+Subdivision,+9+St+Bartholomew+Ave,+Taculing,+" +
            "/data=!4m2!3m1!1s0x33aed179998a4a2f:0xd8ebe45701913e62!8m2!3d10.6654242!4d123.000084"
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(url)
        assertNotNull(result)
        assertEquals("Upstar Variety Store", result!!.suggestedName)
    }

    @Test
    fun primaryPlaceNameKeepsSingleSegment() {
        assertEquals("SM City Bacolod", GoogleMapsLinkParser.primaryPlaceName("SM City Bacolod"))
        assertEquals(
            "Upstar Variety Store",
            GoogleMapsLinkParser.primaryPlaceName(
                "Upstar Variety Store, Doña Juliana Subdivision, 9 St Bartholomew Ave, Taculing,",
            ),
        )
    }

    @Test
    fun formatPlaceDisplayNameDecodesPlusSigns() {
        assertEquals(
            "Upstar Variety Store",
            GoogleMapsLinkParser.formatPlaceDisplayName("Upstar+Variety+Store"),
        )
        assertEquals(
            "Upstar Variety Store",
            GoogleMapsLinkParser.formatPlaceDisplayName("Upstar+Variety+Store,+Bacolod"),
        )
        assertEquals(
            "Upstar Variety Store",
            GoogleMapsLinkParser.formatPlaceDisplayName("Upstar%20Variety%20Store"),
        )
    }

    @Test
    fun parsePreviewBodyExtractsNameFromQueryParam() {
        val bodySnippet = """
            <link href="/maps/preview/place?q=Upstar+Variety+Store&amp;pb=
            %211m3%211d15683%212d123.000084%213d10.6654242%212m3" rel="preload">
        """.trimIndent()
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(bodySnippet)
        assertNotNull(result)
        assertEquals("Upstar Variety Store", result!!.suggestedName)
    }

    @Test
    fun parsePreviewBodyWithBarePercentAndEncodedCoords() {
        val bodySnippet = buildString {
            repeat(120) { append("<style>section{width:100%;height:100%}</style>") }
            append(
                """<link href="/maps/preview/place?pb=%211m3%211d15683""" +
                    """%212d123.000084%213d10.6654242" rel="preload">""",
            )
        }
        val result = GoogleMapsLinkParser.parseCoordinatesFromText(bodySnippet)
        assertNotNull(result)
        assertEquals(10.6654242, result!!.lat, 0.0001)
        assertEquals(123.000084, result.lng, 0.0001)
    }

    @Test
    fun parseInvalidReturnsNull() {
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("not a link"))
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("https://www.google.com/maps"))
        assertNull(GoogleMapsLinkParser.parseCoordinatesFromText("91.0, 200.0"))
    }
}
