package com.kyuusanq3.mixauto.data.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

data class ParsedMapCoordinates(
    val lat: Double,
    val lng: Double,
    val suggestedName: String? = null,
)

object GoogleMapsLinkParser {
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    private val AT_COORDS_REGEX = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val QUERY_COORDS_REGEX = Regex("""[?&](?:q|query)=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val GEO_COORDS_REGEX = Regex("""geo:(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val PLAIN_COORDS_REGEX = Regex(
        """^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""",
    )
    private val PLACE_NAME_REGEX = Regex("""/maps/place/([^/@]+)""")

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
                urlText
            }
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Could not open link — check your connection", e),
            )
        }

        val parsed = parseCoordinatesFromText(expanded)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Could not find coordinates in this link"),
            )
        Result.success(parsed)
    }

    fun parseCoordinatesFromText(text: String): ParsedMapCoordinates? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        PLAIN_COORDS_REGEX.find(trimmed)?.let { match ->
            return coordsFromMatch(match.groupValues[1], match.groupValues[2], trimmed)
        }

        AT_COORDS_REGEX.find(trimmed)?.let { match ->
            return coordsFromMatch(
                match.groupValues[1],
                match.groupValues[2],
                trimmed,
            )
        }

        QUERY_COORDS_REGEX.find(trimmed)?.let { match ->
            return coordsFromMatch(
                match.groupValues[1],
                match.groupValues[2],
                trimmed,
            )
        }

        GEO_COORDS_REGEX.find(trimmed)?.let { match ->
            return coordsFromMatch(
                match.groupValues[1],
                match.groupValues[2],
                trimmed,
            )
        }

        return null
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

    private fun expandShortLink(url: String): String {
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
            connection.inputStream.use { stream ->
                val buffer = ByteArray(512)
                stream.read(buffer)
            }
            return connection.url.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun extractPlaceName(text: String): String? {
        val match = PLACE_NAME_REGEX.find(text) ?: return null
        val encoded = match.groupValues[1]
        val decoded = URLDecoder.decode(encoded.replace('+', ' '), Charsets.UTF_8.name())
            .trim()
        return decoded.takeIf { it.isNotBlank() }
    }
}
