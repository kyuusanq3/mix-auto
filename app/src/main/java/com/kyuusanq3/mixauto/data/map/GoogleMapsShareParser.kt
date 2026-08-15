package com.kyuusanq3.mixauto.data.map

import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

/**
 * Parses text shared from Google Maps (or any app's "Share" sheet) into a droppable POI.
 *
 * Google Maps share text is usually `"Place Name\nhttps://maps.app.goo.gl/xxxx"`. Short links
 * (maps.app.goo.gl / goo.gl) carry no coordinates until redirected; expanded URLs can contain
 * two different coordinate pairs -- `@lat,lng` (map camera center) and `!3d<lat>!4d<lng>`
 * (the actual pinned place) -- so `!3d/!4d` must win when both are present. Google's own text
 * and URL convention is always latitude first, then longitude -- same order used everywhere
 * else in this codebase (SearchResultPlace, PoiSelectionController) -- so no axis swap is
 * needed or performed here.
 */
internal object GoogleMapsShareParser {

    private val URL_REGEX = Regex("""https?://\S+""")
    private val GEO_URI_REGEX = Regex("""geo:(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val PIN_COORDINATES_REGEX = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val QUERY_COORDINATES_REGEX = Regex("""[?&]q=(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val CAMERA_COORDINATES_REGEX = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val PLACE_NAME_REGEX = Regex("""/maps/place/([^/@]+)""")

    private const val MAX_REDIRECT_HOPS = 3
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000

    fun isShareableText(text: String): Boolean =
        GEO_URI_REGEX.containsMatchIn(text) || URL_REGEX.containsMatchIn(text)

    /** Runs blocking network I/O to resolve short links -- call from a background dispatcher. */
    fun parseSharedText(text: String): SearchResultPlace? {
        GEO_URI_REGEX.find(text)?.let { match ->
            val (lat, lng) = match.destructured
            return placeFrom(lat.toDouble(), lng.toDouble(), name = null)
        }

        val url = URL_REGEX.find(text)?.value ?: return null
        val expandedUrl = expandShortLinkIfNeeded(url)

        val coordinates = PIN_COORDINATES_REGEX.find(expandedUrl)
            ?: QUERY_COORDINATES_REGEX.find(expandedUrl)
            ?: CAMERA_COORDINATES_REGEX.find(expandedUrl)
            ?: return null
        val (lat, lng) = coordinates.destructured

        val name = PLACE_NAME_REGEX.find(expandedUrl)?.groupValues?.get(1)?.let(::decodePlaceName)
        return placeFrom(lat.toDouble(), lng.toDouble(), name)
    }

    private fun expandShortLinkIfNeeded(url: String): String {
        val host = runCatching { URL(url).host }.getOrNull() ?: return url
        if (!host.endsWith("goo.gl")) return url

        var currentUrl = url
        repeat(MAX_REDIRECT_HOPS) {
            val location = fetchRedirectLocation(currentUrl) ?: return currentUrl
            currentUrl = location
            val nextHost = runCatching { URL(currentUrl).host }.getOrNull() ?: return currentUrl
            if (!nextHost.endsWith("goo.gl")) return currentUrl
        }
        return currentUrl
    }

    private fun fetchRedirectLocation(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            connection.connect()
            connection.getHeaderField("Location")
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun decodePlaceName(rawSegment: String): String =
        runCatching {
            URLDecoder.decode(rawSegment.replace("+", " "), "UTF-8")
        }.getOrDefault(rawSegment).trim()

    private fun placeFrom(lat: Double, lng: Double, name: String?): SearchResultPlace =
        SearchResultPlace(
            name = name?.takeIf { it.isNotBlank() } ?: "Shared Location",
            subTitle = formatSharedLatLng(lat, lng),
            latitude = lat,
            longitude = lng,
            isDroppedPin = true,
        )

    private fun formatSharedLatLng(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.5f° %s, %.5f° %s",
            kotlin.math.abs(lat),
            latDir,
            kotlin.math.abs(lng),
            lngDir,
        )
    }
}
