package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TomTomRoutingClientTest {

    @Test
    fun parseCalculateRouteJson_readsTrafficSections() {
        val json = """
            {
              "routes": [{
                "summary": {
                  "travelTimeInSeconds": 600,
                  "lengthInMeters": 5000,
                  "trafficDelayInSeconds": 120
                },
                "legs": [{
                  "points": [
                    {"latitude": 10.0, "longitude": 123.0},
                    {"latitude": 10.01, "longitude": 123.01},
                    {"latitude": 10.02, "longitude": 123.02},
                    {"latitude": 10.03, "longitude": 123.03}
                  ]
                }],
                "sections": [
                  {
                    "sectionType": "TRAVEL_MODE",
                    "startPointIndex": 0,
                    "endPointIndex": 3,
                    "travelMode": "car"
                  },
                  {
                    "sectionType": "TRAFFIC",
                    "startPointIndex": 1,
                    "endPointIndex": 2,
                    "magnitudeOfDelay": 3,
                    "delayInSeconds": 90
                  },
                  {
                    "sectionType": "TRAFFIC",
                    "startPointIndex": 2,
                    "endPointIndex": 3,
                    "magnitudeOfDelay": 1
                  }
                ],
                "guidance": {
                  "instructions": [{
                    "point": {"latitude": 10.0, "longitude": 123.0},
                    "message": "Depart",
                    "street": "Test Rd",
                    "distanceInMeters": 5000
                  }]
                }
              }]
            }
        """.trimIndent()

        val result = TomTomRoutingClient.parseCalculateRouteJson(json)
        assertNotNull(result)
        assertEquals(120, result!!.trafficDelaySeconds)
        assertEquals(2, result.trafficSections.size)
        assertEquals(1, result.trafficSections[0].startPointIndex)
        assertEquals(2, result.trafficSections[0].endPointIndex)
        assertEquals(3, result.trafficSections[0].magnitudeOfDelay)
        assertEquals(1, result.trafficSections[1].magnitudeOfDelay)
    }

    @Test
    fun parseTrafficSections_skipsNonTrafficAndInvalidRanges() {
        val route = org.json.JSONObject(
            """
            {
              "sections": [
                {"sectionType": "TRAFFIC", "startPointIndex": 0, "endPointIndex": 1, "magnitudeOfDelay": 2},
                {"sectionType": "ferry", "startPointIndex": 0, "endPointIndex": 1},
                {"sectionType": "TRAFFIC", "startPointIndex": 5, "endPointIndex": 2, "magnitudeOfDelay": 1}
              ]
            }
            """.trimIndent(),
        )
        val sections = TomTomRoutingClient.parseTrafficSections(route)
        assertEquals(1, sections.size)
        assertEquals(2, sections[0].magnitudeOfDelay)
    }

    @Test
    fun tomTomToRouteResult_preservesTrafficSections() {
        val tt = TomTomRouteResult(
            geometryPoints = listOf(10.0 to 123.0, 10.1 to 123.1),
            travelTimeSeconds = 100,
            distanceMeters = 1000.0,
            trafficDelaySeconds = 30,
            steps = listOf(
                TomTomRouteStep(
                    maneuverLat = 10.0,
                    maneuverLng = 123.0,
                    instruction = "Depart",
                    distanceLabel = "1.0 km",
                    streetName = "Main",
                    distanceMeters = 1000.0,
                ),
            ),
            primaryStreet = "Main",
            trafficSections = listOf(
                TomTomTrafficSection(0, 1, 2),
            ),
        )
        val route = LighterTrafficHelper.tomTomToRouteResult(tt)
        assertEquals(1, route.trafficSections.size)
        assertEquals(2, route.trafficSections[0].magnitudeOfDelay)
        assertTrue(route.trafficDelaySeconds == 30)
    }
}