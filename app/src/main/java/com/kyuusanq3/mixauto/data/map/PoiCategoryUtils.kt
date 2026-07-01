package com.kyuusanq3.mixauto.data.map

private val POI_CATEGORY_GROUPS = setOf(
    "food",
    "fuel",
    "health",
    "accommodation",
    "finance",
    "shopping",
    "recreation",
)

internal fun normalizeOvertureCategory(raw: String): String {
    if (raw in POI_CATEGORY_GROUPS) return raw
    return when (raw.lowercase().trim()) {
        "food_and_beverage" -> "food"
        "gas_station" -> "fuel"
        "health_and_medical" -> "health"
        "accommodation" -> "accommodation"
        "financial" -> "finance"
        "retail" -> "shopping"
        "recreation", "entertainment" -> "recreation"
        else -> ""
    }
}

internal fun maplibreClassToCategory(cls: String, sub: String): String {
    val values = listOf(cls, sub).map { it.lowercase().trim() }.filter { it.isNotEmpty() }
    for (value in values) {
        when (value) {
            "food", "restaurant", "fast_food", "cafe", "bar", "pub", "food_court",
            "bakery", "ice_cream", "biergarten", "nightclub",
            -> return "food"
            "fuel" -> return "fuel"
            "hospital", "pharmacy", "clinic", "doctor", "doctors",
            "dentist", "veterinary", "nursing_home",
            -> return "health"
            "hotel", "hostel", "motel", "guest_house", "apartment", "chalet" -> return "accommodation"
            "bank", "atm", "bureau_de_change" -> return "finance"
            "shop", "mall", "department_store", "supermarket",
            "convenience", "clothing", "electronics", "hardware", "marketplace",
            -> return "shopping"
            "attraction", "museum", "park", "stadium", "viewpoint",
            "sports_centre", "swimming_pool", "playground", "cinema", "theatre",
            -> return "recreation"
        }
    }
    return ""
}

internal fun photonToCategory(osmKey: String, osmValue: String): String {
    val key = osmKey.lowercase().trim()
    val value = osmValue.lowercase().trim()
    return when (key) {
        "amenity" -> when (value) {
            "restaurant", "cafe", "fast_food", "bar", "pub", "food_court",
            "bakery", "ice_cream", "biergarten", "nightclub",
            -> "food"
            "fuel" -> "fuel"
            "hospital", "pharmacy", "clinic", "doctors",
            "dentist", "veterinary", "nursing_home",
            -> "health"
            "bank", "atm", "bureau_de_change" -> "finance"
            else -> ""
        }
        "tourism" -> when (value) {
            "hotel", "hostel", "motel", "guest_house", "apartment", "chalet" -> "accommodation"
            "attraction", "museum", "viewpoint",
            "sports_centre", "swimming_pool", "playground", "cinema", "theatre",
            -> "recreation"
            else -> ""
        }
        "shop" -> when (value) {
            "mall", "department_store", "supermarket", "convenience", "clothing",
            "electronics", "hardware", "marketplace",
            -> "shopping"
            else -> "shopping"
        }
        "leisure" -> when (value) {
            "park", "sports_centre", "swimming_pool", "playground" -> "recreation"
            else -> "recreation"
        }
        else -> ""
    }
}
