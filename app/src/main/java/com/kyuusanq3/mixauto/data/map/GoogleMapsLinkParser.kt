package com.kyuusanq3.mixauto.data.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.min

data class ParsedMapCoordinates(
    val lat: Double,
    val lng: Double,
    val suggestedName: String? = null,
)

object GoogleMapsLinkParser {
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESPONSE_BODY_BYTES = 256 * 1024

    private val AT_COORDS_REGEX = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val QUERY_COORDS_REGEX = Regex("""[?&](?:q|query)=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val LL_COORDS_REGEX = Regex("""[?&]ll=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val GEO_COORDS_REGEX = Regex("""geo:(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val PLAIN_COORDS_REGEX = Regex(
        """^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""",
    )
    private val DATA_3D_4D_REGEX = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val LAT_3D_REGEX = Regex("""!3d(-?\d+(?:\.\d+)?)""")
    private val LNG_4D_REGEX = Regex("""!4d(-?\d+(?:\.\d+)?)""")
    private val LNG_2D_REGEX = Regex("""!2d(-?\d+(?:\.\d+)?)""")
    private val PLACE_NAME_REGEX = Regex("""/maps/(?:place|preview/place)/([^/@?]+)""")

    private data class ExpandedLink(
        val finalUrl: String,
        val responseBody: String?,
    )

    suspend fun resolve(input: String): Result<ParsedMapCoordinates> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Link is empty"))
        }

        val direct = parseCoordinatesFromText(trimmed)
        if (direct != null) {
            return@withContext Result.success(direct)
        }

        val urlText = extractUrl(trimmed) ?: return@withContext Result.failure(
            IllegalArgumentException("Could not find coordinates in this link"),
        )

        val expanded = try {
            if (isShortMapsLink(urlText)) {
                expandShortLink(urlText)
            } else {
                ExpandedLink(finalUrl = urlText, responseBody = null)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Could not open link — check your connection", e),
            )
        }

        val parsed = (
            parseCoordinatesFromText(expanded.finalUrl)
                ?: expanded.responseBody?.let { parseCoordinatesFromText(it) }
            )
            ?.let { enrichWithRedirectPlaceName(it, expanded.finalUrl) }
            ?: return@withContext Result.failure(
                IllegalArgumentException("Could not find coordinates in this link"),
            )
        Result.success(parsed)
    }

    private fun enrichWithRedirectPlaceName(
        parsed: ParsedMapCoordinates,
        redirectUrl: String,
    ): ParsedMapCoordinates {
        if (!parsed.suggestedName.isNullOrBlank()) return parsed
        val name = extractPlaceName(redirectUrl) ?: return parsed
        return parsed.copy(suggestedName = name)
    }

    fun parseCoordinatesFromText(text: String): ParsedMapCoordinates? {
        val normalized = normalizeForParsing(text.trim())
        if (normalized.isBlank()) return null

        PLAIN_COORDS_REGEX.find(normalized)?.let { match ->
            return coordsFromMatch(match.groupValues[1], match.groupValues[2], normalized)
        }

        parseDataParameterCoords(normalized)?.let { return it }

        AT_COORDS_REGEX.find(normalized)?.let { match ->
            return coordsFromMatch(
                match.groupValues[1],
                match.groupValues[2],
                normalized,
            )
        }

        QUERY_COORDS_REGEX.find(normalized)?.let { match ->
            return coordsFromMatch(
                match.groupValues[1],
                match.groupValues[2],
                normalized,
            )
        }

        LL_COORDS_REGEX.find(normalized)?.let { match ->
            return coordsFromMatch(
                match.groupValues[1],
                match.groupValues[2],
                normalized,
            )
        }

        GEO_COORDS_REGEX.find(normalized)?.let { match ->
            return coordsFromMatch(
                match.groupValues[1],
                match.groupValues[2],
                normalized,
            )
        }

        return null
    }

    private fun parseDataParameterCoords(text: String): ParsedMapCoordinates? {
        DATA_3D_4D_REGEX.findAll(text).lastOrNull()?.let { match ->
            return coordsFromMatch(match.groupValues[1], match.groupValues[2], text)
        }

        val latMatches = LAT_3D_REGEX.findAll(text)
            .mapNotNull { match ->
                val value = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                if (value !in -90.0..90.0) return@mapNotNull null
                match.range.first to value
            }
            .toList()
        if (latMatches.isEmpty()) return null

        val lngMatches = (LNG_4D_REGEX.findAll(text) + LNG_2D_REGEX.findAll(text))
            .mapNotNull { match ->
                val value = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                if (value !in -180.0..180.0) return@mapNotNull null
                match.range.first to value
            }
            .sortedBy { it.first }

        val latIndex = latMatches.last().first
        val (_, lat) = latMatches.last()
        val lng = lngMatches
            .filter { (index, _) -> kotlin.math.abs(index - latIndex) <= 500 }
            .minByOrNull { (index, _) -> kotlin.math.abs(index - latIndex) }
            ?.second
            ?: return null

        return coordsFromMatch(
            latText = lat.toString(),
            lngText = lng.toString(),
            sourceText = text,
        )
    }

    private fun normalizeForParsing(text: String): String {
        val withoutEntities = text.replace("&amp;", "&")
        return try {
            URLDecoder.decode(withoutEntities, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            withoutEntities
        }
    }

    private fun coordsFromMatch(
        latText: String,
        lngText: String,
        sourceText: String,
    ): ParsedMapCoordinates? {
        val lat = latText.toDoubleOrNull() ?: return null
        val lng = lngText.toDoubleOrNull() ?: return null
        if (!isValidCoordinate(lat, lng)) return null
        return ParsedMapCoordinates(
            lat = lat,
            lng = lng,
            suggestedName = extractPlaceName(sourceText),
        )
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean =
        lat in -90.0..90.0 && lng in -180.0..180.0

    private fun extractUrl(text: String): String? {
        val httpMatch = Regex("""https?://\S+""").find(text)?.value ?: return null
        return httpMatch.trimEnd(',', '.', ')', ']', '"', '\'')
    }

    private fun isShortMapsLink(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("maps.app.goo.gl") || lower.contains("goo.gl/maps")
    }

    private fun expandShortLink(url: String): ExpandedLink {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        try {
            val code = connection.responseCode
            if (code !in 200..399) {
                throw IllegalStateException("HTTP $code")
            }
            val body = connection.inputStream.use { stream ->
                readLimitedUtf8(stream, MAX_RESPONSE_BODY_BYTES)
            }
            return ExpandedLink(
                finalUrl = connection.url.toString(),
                responseBody = body,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedUtf8(stream: InputStream, maxBytes: Int): String {
        val buffer = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val read = stream.read(buffer, offset, min(8192, maxBytes - offset))
            if (read <= 0) break
            offset += read
        }
        return String(buffer, 0, offset, Charsets.UTF_8)
    }

    private fun extractPlaceName(text: String): String? {
        val normalized = normalizeForParsing(text)
        val match = PLACE_NAME_REGEX.find(normalized) ?: return null
        val encoded = match.groupValues[1]
        val decoded = URLDecoder.decode(encoded.replace('+', ' '), Charsets.UTF_8.name())
            .trim()
            .trimEnd('/')
        return decoded.takeIf { it.isNotBlank() }
    }
}
