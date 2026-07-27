package com.kyuusanq3.mixauto.data.map

object MapStyleConstants {
    /** Bundled automotive Liberty fork — must match [OfflineMapRepository] region definitions. */
    const val VECTOR_STYLE_URI = "asset://map/mix-auto-driving.json"

    /**
     * Minimal MapLibre style that sources raster tiles from the public OSM tile server.
     * Global coverage, no API key required. Raster tiles don't support sharp 3D perspective
     * so tilt is kept moderate (30°). Replace with a vector style for a crisper driving view.
     */
    val OSM_STYLE_JSON = """
        {
            "version": 8,
            "sources": {
                "osm": {
                    "type": "raster",
                    "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                    "tileSize": 256,
                    "attribution": "\u00a9 OpenStreetMap contributors"
                }
            },
            "layers": [{
                "id": "osm",
                "type": "raster",
                "source": "osm"
            }]
        }
    """.trimIndent()
}
