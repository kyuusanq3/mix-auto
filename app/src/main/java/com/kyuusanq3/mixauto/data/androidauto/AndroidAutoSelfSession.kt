package com.kyuusanq3.mixauto.data.androidauto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mix Auto as the head unit on this same device.
 * Newer Android Auto listens on 5277. Android Auto 10.9 dials us on 5288.
 */
internal data class InstalledAndroidAuto(
    val versionName: String,
    val usesHeadUnitServer: Boolean,
)

internal class AndroidAutoSelfSession(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onRunning: (Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var socket: Socket? = null
    private var server: ServerSocket? = null
    private var phoneAcceptedHeadUnit = false

    fun start(installed: InstalledAndroidAuto) {
        job?.cancel()
        closeSockets()
        phoneAcceptedHeadUnit = false
        onRunning(true)
        job = scope.launch {
            try {
                runSession(installed)
            } catch (_: CancellationException) {
                onStatus("Self mode stopped.")
            } catch (error: Exception) {
                onStatus(error.message ?: "Self mode stopped.")
            } finally {
                closeSockets()
                onRunning(false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        closeSockets()
    }

    private suspend fun runSession(installed: InstalledAndroidAuto) {
        val connected = openSocket(installed) ?: return
        socket = connected
        connected.soTimeout = READ_TIMEOUT_MS
        val input = DataInputStream(connected.getInputStream())
        val output = connected.getOutputStream()
        onStatus("Connected. Asking Android Auto for a session.")
        writeMessage(output, GalFrames.VERSION_REQUEST, shortBytes(1, 6))
        val version = readFrame(input)
        if (version.messageType != GalFrames.VERSION_RESPONSE) {
            throw IOException(
                "Android Auto replied with message ${version.messageType} instead of a version.",
            )
        }
        onStatus("Android Auto answered ${shortsOf(version.payload).joinToString()}. Encrypting the session.")
        handshake(clientEngine(), input, output)
        onStatus("Encrypted. Telling Android Auto this head unit is ready.")
        writeMessage(output, GalFrames.AUTH_COMPLETE, byteArrayOf(0x08, 0x00))
        while (coroutineContext.isActive) {
            val frame = try {
                readFrame(input)
            } catch (_: SocketTimeoutException) {
                continue
            }
            handleControl(frame, output)
        }
    }

    private fun openSocket(installed: InstalledAndroidAuto): Socket? {
        onStatus("Connecting to Android Auto on this device, port 5277.")
        try {
            return Socket().also { client ->
                client.connect(InetSocketAddress(LOCAL_HOST, HEAD_UNIT_SERVER_PORT), CONNECT_TIMEOUT_MS)
            }
        } catch (_: Exception) {
            if (installed.usesHeadUnitServer) {
                onStatus(
                    "Android Auto ${installed.versionName} did not accept port 5277. " +
                        "In Android Auto, open the version screen, tap Version 10 times, " +
                        "then turn on Start head unit server and try again.",
                )
                return null
            }
        }
        return listenForDirectAndroidAuto(installed.versionName)
    }

    private fun listenForDirectAndroidAuto(versionName: String): Socket? {
        onStatus("Android Auto $versionName is not on port 5277. Waiting for it on port 5288.")
        val listening = ServerSocket()
        server = listening
        listening.reuseAddress = true
        listening.soTimeout = LISTEN_TIMEOUT_MS
        listening.bind(InetSocketAddress(LOCAL_HOST, DIRECT_PROJECTION_PORT))
        askDirectAndroidAutoToConnect()
        return try {
            listening.accept()
        } catch (_: Exception) {
            onStatus("Android Auto $versionName did not connect to port 5288.")
            null
        }
    }

    private fun askDirectAndroidAutoToConnect() {
        val receiver = Intent(WIRELESS_START_BROADCAST).apply {
            component = ComponentName(GEARHEAD_PACKAGE, WIRELESS_STARTUP_RECEIVER)
            putExtra("ip_address", LOCAL_HOST)
            putExtra("projection_port", DIRECT_PROJECTION_PORT)
        }
        appContext.sendBroadcast(receiver)
        val activity = Intent(START_WIRELESS_PROJECTION).apply {
            component = ComponentName(GEARHEAD_PACKAGE, WIRELESS_STARTUP_ACTIVITY)
            putExtra("PARAM_HOST_ADDRESS", LOCAL_HOST)
            putExtra("PARAM_SERVICE_PORT", DIRECT_PROJECTION_PORT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(activity)
        } catch (_: Exception) {
            // The broadcast is the path Android Auto 10.9 actually logs.
        }
    }

    private fun handleControl(frame: GalFrame, output: OutputStream) {
        when (frame.messageType) {
            GalFrames.PING_REQUEST -> {
                writeMessage(output, GalFrames.PING_RESPONSE, frame.payload)
                if (!phoneAcceptedHeadUnit) {
                    onStatus("Android Auto is talking. Waiting for it to ask for the screen.")
                }
            }
            GalFrames.SERVICE_DISCOVERY_REQUEST -> {
                phoneAcceptedHeadUnit = true
                onStatus(
                    "Android Auto accepted Mix Auto as the head unit and asked for the screen. " +
                        "The picture itself is the next step.",
                )
            }
            GalFrames.SHUTDOWN_REQUEST -> {
                writeMessage(output, GalFrames.SHUTDOWN_RESPONSE, byteArrayOf())
                throw IOException("Android Auto closed the session.")
            }
            else -> {
                onStatus("Android Auto sent message ${frame.messageType} on channel ${frame.channel}.")
            }
        }
    }

    private fun clientEngine(): SSLEngine {
        val password = KEYSTORE_PASSWORD.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        appContext.assets.open(KEYSTORE_ASSET).use { keyStore.load(it, password) }
        val keys = javax.net.ssl.KeyManagerFactory.getInstance(
            javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm(),
        )
        keys.init(keyStore, password)
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val ssl = SSLContext.getInstance("TLSv1.2")
        ssl.init(keys.keyManagers, trustAll, SecureRandom())
        val engine = ssl.createSSLEngine(LOCAL_HOST, HEAD_UNIT_SERVER_PORT)
        engine.useClientMode = true
        val params = engine.sslParameters
        params.endpointIdentificationAlgorithm = null
        engine.sslParameters = params
        return engine
    }

    private fun handshake(engine: SSLEngine, input: DataInputStream, output: OutputStream) {
        val clear = ByteBuffer.allocate(engine.session.applicationBufferSize)
        engine.beginHandshake()
        var steps = 0
        while (
            engine.handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
            engine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
        ) {
            if (++steps > 64) throw IOException("Encryption handshake did not finish.")
            when (engine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_TASK -> runDelegatedTasks(engine)
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> wrapHandshake(engine, output)
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> unwrapHandshake(engine, input, clear)
                else -> {
                    if (engine.handshakeStatus.name == "NEED_UNWRAP_AGAIN") {
                        unwrapBuffered(engine, ByteBuffer.allocate(0), clear)
                    } else {
                        throw IOException("Encryption handshake status ${engine.handshakeStatus}.")
                    }
                }
            }
        }
    }

    private fun wrapHandshake(engine: SSLEngine, output: OutputStream) {
        val packet = ByteBuffer.allocate(engine.session.packetBufferSize)
        val result = engine.wrap(ByteBuffer.allocate(0), packet)
        if (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            throw IOException("Encryption output did not fit.")
        }
        packet.flip()
        if (!packet.hasRemaining()) return
        val bytes = ByteArray(packet.remaining())
        packet.get(bytes)
        writeMessage(output, GalFrames.SSL_HANDSHAKE, bytes)
    }

    private fun unwrapHandshake(engine: SSLEngine, input: DataInputStream, clear: ByteBuffer) {
        val frame = readFrame(input)
        if (frame.messageType != GalFrames.SSL_HANDSHAKE) {
            throw IOException("Expected encryption data and got message ${frame.messageType}.")
        }
        unwrapBuffered(engine, ByteBuffer.wrap(frame.payload), clear)
    }

    private fun unwrapBuffered(engine: SSLEngine, source: ByteBuffer, clear: ByteBuffer) {
        while (source.hasRemaining()) {
            clear.clear()
            val result = engine.unwrap(source, clear)
            if (result.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                runDelegatedTasks(engine)
            }
            if (result.status == SSLEngineResult.Status.BUFFER_UNDERFLOW) return
            if (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                throw IOException("Encryption input did not fit.")
            }
            if (result.status == SSLEngineResult.Status.CLOSED) return
        }
    }

    private fun runDelegatedTasks(engine: SSLEngine) {
        while (true) {
            val task = engine.delegatedTask ?: return
            task.run()
        }
    }

    private fun readFrame(input: DataInputStream): GalFrame {
        var assembled: GalFrame? = null
        while (true) {
            val frame = readOneFrame(input)
            val previous = assembled
            assembled = if (previous == null) {
                frame
            } else {
                frame.copy(payload = previous.payload + frame.payload)
            }
            val complete = frame.flags and GalFrames.FLAG_LAST != 0 || frame.flags == 0
            if (complete) return assembled
        }
    }

    private fun readOneFrame(input: DataInputStream): GalFrame {
        val header = ByteArray(4)
        input.readFully(header)
        val size = ((header[2].toInt() and 0xff) shl 8) or (header[3].toInt() and 0xff)
        if (size < 2 || size > MAX_FRAME_BYTES) {
            val shown = header.joinToString(" ") { "%02X".format(it) }
            throw IOException("Android Auto sent an unexpected header $shown.")
        }
        val body = ByteArray(size)
        input.readFully(body)
        val type = ((body[0].toInt() and 0xff) shl 8) or (body[1].toInt() and 0xff)
        return GalFrame(
            channel = header[0].toInt() and 0xff,
            flags = header[1].toInt() and 0xff,
            messageType = type,
            payload = body.copyOfRange(2, body.size),
        )
    }

    private fun writeMessage(output: OutputStream, messageType: Int, payload: ByteArray) {
        output.write(
            GalFrames.encode(
                channel = GalFrames.CHANNEL_CONTROL,
                messageType = messageType,
                payload = payload,
            ),
        )
        output.flush()
    }

    private fun closeSockets() {
        socket?.close()
        socket = null
        server?.close()
        server = null
    }

    private companion object {
        const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
        const val WIRELESS_STARTUP_ACTIVITY =
            "com.google.android.apps.auto.wireless.setup.service.impl.WirelessStartupActivity"
        const val WIRELESS_STARTUP_RECEIVER =
            "com.google.android.apps.auto.wireless.setup.receiver.WirelessStartupReceiver"
        const val START_WIRELESS_PROJECTION =
            "com.google.android.projection.gearhead.START_WIRELESS_PROJECTION"
        const val WIRELESS_START_BROADCAST =
            "com.google.android.apps.auto.wireless.setup.receiver.wirelessstartup.START"
        const val LOCAL_HOST = "127.0.0.1"
        const val HEAD_UNIT_SERVER_PORT = 5277
        const val DIRECT_PROJECTION_PORT = 5288
        const val CONNECT_TIMEOUT_MS = 2_000
        const val LISTEN_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 8_000
        const val MAX_FRAME_BYTES = 1_000_000
        const val KEYSTORE_ASSET = "androidauto/mix-auto-hu.p12"
        const val KEYSTORE_PASSWORD = "mixauto"
    }
}

internal fun shortBytes(first: Int, second: Int): ByteArray {
    return byteArrayOf(
        (first shr 8).toByte(),
        (first and 0xff).toByte(),
        (second shr 8).toByte(),
        (second and 0xff).toByte(),
    )
}

internal fun shortsOf(payload: ByteArray): List<Int> {
    val values = ArrayList<Int>(payload.size / 2)
    var index = 0
    while (index + 1 < payload.size) {
        values.add(((payload[index].toInt() and 0xff) shl 8) or (payload[index + 1].toInt() and 0xff))
        index += 2
    }
    return values
}
