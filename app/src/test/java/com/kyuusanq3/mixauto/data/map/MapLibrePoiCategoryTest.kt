package com.kyuusanq3.mixauto.data.map

import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class MapLibrePoiCategoryTest {

    @Test
    fun normalizeOvertureCategory_mapsKnownRawValues() {
        assertEquals("food", normalizeOvertureCategory("food_and_beverage"))
        assertEquals("fuel", normalizeOvertureCategory("gas_station"))
        assertEquals("food", normalizeOvertureCategory("food"))
    }

    @Test
    fun maplibreClassToCategory_mapsExpandedLibertyClasses() {
        assertEquals("food", maplibreClassToCategory("amenity", "bakery"))
        assertEquals("health", maplibreClassToCategory("amenity", "dentist"))
        assertEquals("shopping", maplibreClassToCategory("shop", "convenience"))
        assertEquals("recreation", maplibreClassToCategory("leisure", "playground"))
        assertEquals("", maplibreClassToCategory("amenity", "school"))
    }

    @Test
    fun photonToCategory_mapsExpandedOsmTags() {
        assertEquals("food", photonToCategory("amenity", "bakery"))
        assertEquals("accommodation", photonToCategory("tourism", "hotel"))
        assertEquals("shopping", photonToCategory("shop", "convenience"))
    }

    @Test
    fun preferPoiEntry_prefersOvertureOverVector() {
        val overture = SearchResultPlace(
            name = "Shell",
            subTitle = "Main St",
            latitude = 10.0,
            longitude = 122.0,
            category = "fuel",
            poiSource = POI_SOURCE_OVERTURE,
        )
        val vector = SearchResultPlace(
            name = "Shell",
            subTitle = "",
            latitude = 10.00001,
            longitude = 122.00001,
            category = "",
            poiSource = POI_SOURCE_VECTOR,
        )
        val merged = preferPoiEntry(vector, overture)
        assertEquals("fuel", merged.category)
        assertEquals(POI_SOURCE_OVERTURE, merged.poiSource)
        assertEquals("Main St", merged.subTitle)
    }

    @Test
    fun preferPoiEntry_fillsBlankCategoryFromIncoming() {
        val existing = SearchResultPlace(
            name = "Cafe",
            subTitle = "",
            latitude = 1.0,
            longitude = 2.0,
            category = "",
            poiSource = POI_SOURCE_VECTOR,
        )
        val incoming = SearchResultPlace(
            name = "Cafe",
            subTitle = "City",
            latitude = 1.0,
            longitude = 2.0,
            category = "food",
            poiSource = POI_SOURCE_PHOTON,
        )
        val merged = preferPoiEntry(existing, incoming)
        assertEquals("food", merged.category)
        assertEquals(POI_SOURCE_PHOTON, merged.poiSource)
    }

    @Test
    fun poiCacheKey_roundsToFiveDecimalPlaces() {
        assertEquals("10.12346,122.98765", poiCacheKey(
            SearchResultPlace(
                name = "x",
                subTitle = "",
                latitude = 10.123456,
                longitude = 122.987654,
            ),
        ))
    }

    @Test
    fun sortPoiPinsForMerge_putsCategorizedOvertureFirst() {
        val sorted = sortPoiPinsForMerge(
            listOf(
                SearchResultPlace("B", "", 1.0, 1.0, category = "", poiSource = POI_SOURCE_VECTOR),
                SearchResultPlace("A", "", 2.0, 2.0, category = "food", poiSource = POI_SOURCE_OVERTURE),
            ),
        )
        assertEquals("food", sorted.first().category)
    }
}
