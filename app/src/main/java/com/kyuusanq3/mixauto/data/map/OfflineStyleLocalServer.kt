package com.kyuusanq3.mixauto.data.map

import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serves the bundled offline-pack style JSON over loopback HTTP.
 * OfflineManager only accepts http(s) style URLs — not asset:// or file://.
 */
internal object OfflineStyleLocalServer {

    private const val TAG = "OfflineStyleLocalServer"
    private const val STYLE_PATH = "/mix-auto-offline-pack.json"
    private const val STYLE_PORT = 28777

    @Volatile
    private var styleFile: File? = null

    @Volatile
    private var styleUrl: String? = null

    private val started = AtomicBoolean(false)

    fun ensureRunning(styleFile: File): String {
        this.styleFile = styleFile
        if (started.compareAndSet(false, true)) {
            val serverSocket = try {
                ServerSocket(STYLE_PORT, 5, InetAddress.getByName("127.0.0.1"))
            } catch (_: Exception) {
                ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
            }
            val port = serverSocket.localPort
            styleUrl = "http://127.0.0.1:$port$STYLE_PATH"
            Log.i(TAG, "Serving offline style at $styleUrl")
            Thread(
                {
                    while (true) {
                        try {
                            val client = serverSocket.accept()
                            Thread({ serve(client) }, "offline-style-http").start()
                        } catch (exception: Exception) {
                            Log.w(TAG, "Offline style server accept failed", exception)
                            break
                        }
                    }
                },
                "offline-style-accept",
            ).apply {
                isDaemon = true
                start()
            }
        }
        return styleUrl ?: error("Offline style server failed to start")
    }

    private fun serve(socket: Socket) {
        val file = styleFile
        if (file == null || !file.exists()) {
            socket.close()
            return
        }
        try {
            socket.soTimeout = 30_000
            socket.getInputStream().bufferedReader().use { reader ->
                val requestLine = reader.readLine() ?: return
                if (!requestLine.startsWith("GET ")) return
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    line = reader.readLine()
                }
                val bytes = file.readBytes()
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().use { output ->
                    output.write(header.toByteArray(Charsets.US_ASCII))
                    output.write(bytes)
                    output.flush()
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Offline style request failed", exception)
        } finally {
            runCatching { socket.close() }
        }
    }
}
