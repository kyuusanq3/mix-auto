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
    private val ENC_DATA_3D_4D_REGEX = Regex(
        """%213d(-?\d+(?:\.\d+)?)%214d(-?\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val ENC_LAT_3D_REGEX = Regex("""%213d(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val ENC_LNG_4D_REGEX = Regex("""%214d(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val ENC_LNG_2D_REGEX = Regex("""%212d(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val PLACE_NAME_REGEX = Regex("""/maps/(?:place|preview/place)/([^/@?]+)""")
    private val QUERY_PLACE_NAME_REGEX = Regex("""[?&]q=([^&]+)""")

    private data class ExpandedLink(
        val finalUrl: String,
        val responseBody: String?,
    )

    suspend fun resolve(input: String): Result<ParsedMapCoordinates> = withContext(Dispatchers.IO) {
        val trimmed = sanitizeInput(input)
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
        val redirectName = extractPlaceName(redirectUrl)
        val parsedName = parsed.suggestedName?.let(::formatPlaceDisplayName)?.takeIf { it.isNotBlank() }
        val name = parsedName ?: redirectName ?: return parsed
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
        parseDecodedProtobufCoords(text)?.let { return it }
        parseEncodedProtobufCoords(text)?.let { return it }
        val normalized = normalizeForParsing(text)
        if (normalized !== text) {
            parseDecodedProtobufCoords(normalized)?.let { return it }
        }
        return null
    }

    private fun parseDecodedProtobufCoords(text: String): ParsedMapCoordinates? {
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

    private fun parseEncodedProtobufCoords(text: String): ParsedMapCoordinates? {
        ENC_DATA_3D_4D_REGEX.findAll(text).lastOrNull()?.let { match ->
            return coordsFromMatch(match.groupValues[1], match.groupValues[2], text)
        }

        val latMatches = ENC_LAT_3D_REGEX.findAll(text)
            .mapNotNull { match ->
                val value = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                if (value !in -90.0..90.0) return@mapNotNull null
                match.range.first to value
            }
            .toList()
        if (latMatches.isEmpty()) return null

        val lngMatches = (ENC_LNG_4D_REGEX.findAll(text) + ENC_LNG_2D_REGEX.findAll(text))
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
        // Full Google Maps HTML contains bare '%' (CSS, etc.) that breaks URLDecoder.decode.
        if (withoutEntities.length > 2_048) {
            return withoutEntities
        }
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
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
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
        PLACE_NAME_REGEX.find(normalized)?.let { match ->
            return formatPlaceDisplayName(match.groupValues[1]).takeIf { it.isNotBlank() }
        }
        QUERY_PLACE_NAME_REGEX.find(normalized)?.let { match ->
            val candidate = match.groupValues[1].trim()
            if (PLAIN_COORDS_REGEX.matches(candidate)) return null
            return formatPlaceDisplayName(candidate).takeIf { it.isNotBlank() }
        }
        return null
    }

    /** Decodes URL-encoded place text and keeps only the primary name segment. */
    internal fun formatPlaceDisplayName(raw: String): String {
        val plusAsSpace = raw.replace('+', ' ')
        val decoded = try {
            URLDecoder.decode(plusAsSpace, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            plusAsSpace
        }
        // %2B decodes to '+' — treat any remaining plus signs as spaces for display.
        return primaryPlaceName(decoded.replace('+', ' ').trim())
    }

    /** Google Maps place paths often encode the full address after the business name. */
    internal fun primaryPlaceName(decoded: String): String {
        val trimmed = decoded.trim().trimEnd(',')
        val commaIndex = trimmed.indexOf(',')
        return if (commaIndex < 0) trimmed else trimmed.substring(0, commaIndex).trim()
    }

    private fun sanitizeInput(text: String): String = text
        .replace("\u200B", "")
        .replace("\u200C", "")
        .replace("\u200D", "")
        .replace("\uFEFF", "")
        .trim()
}
