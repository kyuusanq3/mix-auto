package com.kyuusanq3.mixauto.data.map

import android.util.Log
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import java.io.BufferedReader
import java.io.InputStreamReader
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

    private const val TAG = "MixAutoShare"

    private val URL_REGEX = Regex("""https?://\S+""")
    private val GEO_URI_REGEX = Regex("""geo:(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val PIN_COORDINATES_REGEX = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val QUERY_COORDINATES_REGEX = Regex("""[?&]q=(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val CAMERA_COORDINATES_REGEX = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val PLACE_NAME_REGEX = Regex("""/maps/place/([^/@]+)""")

    private const val MAX_REDIRECT_HOPS = 5
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val MAX_BODY_CHARS = 200_000

    // Google's short-link redirector serves a plain HTTP 302 to a browser-looking client, but
    // can fall back to a 200 "Redirecting..." interstitial (with the target URL embedded in the
    // HTML/JS body, not a Location header) for requests with no/unusual User-Agent -- which is
    // exactly what java.net.HttpURLConnection sends by default. Spoofing a normal mobile Chrome
    // UA reliably gets the real 302 back.
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    fun isShareableText(text: String): Boolean =
        GEO_URI_REGEX.containsMatchIn(text) || URL_REGEX.containsMatchIn(text)

    /**
     * Returns the place-name text Google Maps puts on the line above the share link
     * (e.g. `"Place Name\nhttps://maps.app.goo.gl/xxxx"`), for use as a geocoder fallback
     * query when the URL itself carries no coordinates (short-link business/POI shares).
     */
    fun extractNameHint(text: String): String? {
        val urlStart = URL_REGEX.find(text)?.range?.first
        val geoStart = GEO_URI_REGEX.find(text)?.range?.first
        val cut = minOf(urlStart ?: text.length, geoStart ?: text.length)
        return text.substring(0, cut).trim().takeIf { it.isNotBlank() && it.length <= 120 }
    }

    /** Result of [parseSharedText]: either a fully-resolved place, or just a name to geocode. */
    sealed class ParsedShare {
        data class Resolved(val place: SearchResultPlace) : ParsedShare()
        data class NeedsGeocode(val nameHint: String) : ParsedShare()
    }

    /** Runs blocking network I/O to resolve short links -- call from a background dispatcher. */
    fun parseSharedText(text: String): ParsedShare? {
        Log.d(TAG, "parseSharedText: raw text=\"${text.take(300)}\"")

        GEO_URI_REGEX.find(text)?.let { match ->
            val (lat, lng) = match.destructured
            Log.d(TAG, "parseSharedText: matched geo: URI -> lat=$lat lng=$lng")
            return ParsedShare.Resolved(placeFrom(lat.toDouble(), lng.toDouble(), name = null))
        }

        val url = URL_REGEX.find(text)?.value
        if (url == null) {
            Log.w(TAG, "parseSharedText: no http(s) URL or geo: URI found in shared text")
            return null
        }
        Log.d(TAG, "parseSharedText: extracted URL=$url")

        val expandedContent = expandShortLinkIfNeeded(url)
        Log.d(
            TAG,
            "parseSharedText: expanded content (${expandedContent.length} chars)=" +
                expandedContent.take(500),
        )

        val name = PLACE_NAME_REGEX.find(expandedContent)?.groupValues?.get(1)?.let(::decodePlaceName)

        val coordinates = PIN_COORDINATES_REGEX.find(expandedContent)
            ?: QUERY_COORDINATES_REGEX.find(expandedContent)
            ?: CAMERA_COORDINATES_REGEX.find(expandedContent)
        if (coordinates == null) {
            // Business/POI shares often encode the place purely as a Google feature ID
            // (`!1s0x...:0x...`) with no @lat,lng or !3d/!4d anywhere in the URL -- only
            // Google's own servers can resolve that ID. The place *name* is still present in
            // the URL path though (/maps/place/<name>/), so fall back to geocoding that.
            Log.w(TAG, "parseSharedText: no !3d/!4d, q=, or @lat,lng pattern found in expanded content")
            return name?.let {
                Log.d(TAG, "parseSharedText: falling back to geocoding URL place-name=$it")
                ParsedShare.NeedsGeocode(it)
            }
        }
        val (lat, lng) = coordinates.destructured
        Log.d(TAG, "parseSharedText: matched coordinates lat=$lat lng=$lng via '${coordinates.value}'")
        Log.d(TAG, "parseSharedText: resolved name=$name")
        return ParsedShare.Resolved(placeFrom(lat.toDouble(), lng.toDouble(), name))
    }

    /**
     * Follows short-link redirects to a final Google Maps URL. Returns the final URL string when
     * a normal HTTP redirect chain resolves, or the raw response body of the last hop when Google
     * serves a non-redirect interstitial instead -- either way the caller's regexes can still find
     * embedded `@lat,lng` / `!3d/!4d` coordinates in whatever string comes back.
     */
    private fun expandShortLinkIfNeeded(url: String): String {
        val host = runCatching { URL(url).host }.getOrNull()
        if (host == null) {
            Log.w(TAG, "expandShortLinkIfNeeded: could not parse host of $url, returning as-is")
            return url
        }
        if (!host.endsWith("goo.gl")) {
            Log.d(TAG, "expandShortLinkIfNeeded: host=$host is not a short link, skipping expansion")
            return url
        }

        var currentUrl = url
        repeat(MAX_REDIRECT_HOPS) { hop ->
            Log.d(TAG, "expandShortLinkIfNeeded: hop $hop fetching $currentUrl")
            val result = fetchRedirectOrBody(currentUrl)
            if (result == null) {
                Log.w(TAG, "expandShortLinkIfNeeded: hop $hop request failed, stopping at $currentUrl")
                return currentUrl
            }
            when (result) {
                is RedirectResult.Location -> {
                    Log.d(TAG, "expandShortLinkIfNeeded: hop $hop -> Location: ${result.url}")
                    currentUrl = result.url
                    val nextHost = runCatching { URL(currentUrl).host }.getOrNull()
                    if (nextHost == null) {
                        Log.w(TAG, "expandShortLinkIfNeeded: could not parse redirected host, stopping")
                        return currentUrl
                    }
                    if (!nextHost.endsWith("goo.gl")) {
                        Log.d(TAG, "expandShortLinkIfNeeded: reached final host=$nextHost")
                        return currentUrl
                    }
                }
                is RedirectResult.Body -> {
                    Log.d(TAG, "expandShortLinkIfNeeded: hop $hop -> 200 body (${result.text.length} chars)")
                    return result.text
                }
            }
        }
        Log.w(TAG, "expandShortLinkIfNeeded: exceeded $MAX_REDIRECT_HOPS hops, returning last URL")
        return currentUrl
    }

    private sealed class RedirectResult {
        data class Location(val url: String) : RedirectResult()
        data class Body(val text: String) : RedirectResult()
    }

    private fun fetchRedirectOrBody(url: String): RedirectResult? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                setRequestProperty("Accept", "text/html")
            }
            val responseCode = connection.responseCode
            Log.d(TAG, "fetchRedirectOrBody: $url -> HTTP $responseCode")
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                if (location != null) {
                    return RedirectResult.Location(location)
                }
                Log.w(TAG, "fetchRedirectOrBody: $responseCode with no Location header")
            }
            if (responseCode >= 400) {
                Log.w(TAG, "fetchRedirectOrBody: $url -> HTTP $responseCode, link is likely invalid/expired")
                return RedirectResult.Body("")
            }
            RedirectResult.Body(readBodySafely(connection))
        } catch (e: Exception) {
            Log.e(TAG, "fetchRedirectOrBody: request to $url failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBodySafely(connection: HttpURLConnection): String =
        runCatching {
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val buffer = CharArray(MAX_BODY_CHARS)
                val read = reader.read(buffer, 0, MAX_BODY_CHARS)
                if (read > 0) String(buffer, 0, read) else ""
            }
        }.onFailure { e -> Log.e(TAG, "readBodySafely: failed to read response body", e) }
            .getOrDefault("")

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
