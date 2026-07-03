import java.net.HttpURLConnection
import java.net.URL

fun expand(url: String) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connectTimeout = 15000
        readTimeout = 15000
    }
    conn.inputStream.use { it.readBytes().take(50000) }
    println("FINAL: ${conn.url}")
    val body = conn.inputStream.bufferedReader().readText().take(8000)
    println("BODY SNIP: ${body.take(2000)}")
    conn.disconnect()
}

// test with a public maps place URL format
